/*
 * (C) Copyright 2016 PANTHEON.tech, s.r.o. and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.pantheon.triemap;

import static tech.pantheon.triemap.Constants.HASH_BITS;
import static tech.pantheon.triemap.Constants.LEVEL_BITS;
import static tech.pantheon.triemap.Constants.MAX_DEPTH;
import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.jdt.annotation.Nullable;

final class CNode<K, V> extends MainNode<K, V> {
    private static final Branch<?, ?>[] EMPTY_ARRAY = new Branch[0];

    final int bitmap;
    final Branch<K, V>[] array;
    final Gen gen;

    // Since concurrent computation should lead to same results we can update this field without any synchronization.
    private volatile int csize = NO_SIZE;

    @SafeVarargs
    private CNode(final CNode<K, V> prev, final Gen gen, final int bitmap, final Branch<K, V>... array) {
        super(prev);
        this.bitmap = bitmap;
        this.array = array;
        this.gen = gen;
    }

    @SafeVarargs
    private CNode(final Gen gen, final int bitmap, final Branch<K, V>... array) {
        this.bitmap = bitmap;
        this.array = array;
        this.gen = gen;
    }

    @SuppressWarnings("unchecked")
    CNode(final Gen gen) {
        this(gen, 0, (Branch<K, V>[]) EMPTY_ARRAY);
    }

    Object lookup(final TrieMap<K, V> ct, final Gen startGen, final int hc, final K key, final int lev,
            final INode<K, V> parent) {
        // 1) a multinode
        final int idx = hc >>> lev & 0x1f;
        final int flag = 1 << idx;

        if ((bitmap & flag) == 0) {
            // 1a) bitmap shows no binding
            return null;
        }

        // 1b) bitmap contains a value - descend
        final int pos = bitmap == 0xffffffff ? idx : Integer.bitCount(bitmap & flag - 1);
        final var sub = array[pos];
        if (sub instanceof INode<K, V> in) {
            // try to renew if needed and enter next level
            return ct.isReadOnly() || startGen == in.gen || renew(ct, parent, startGen)
                ? in.lookup(ct, startGen, hc, key, lev + LEVEL_BITS, parent) : TrieMap.RESTART;
        } else if (sub instanceof SNode<K, V> sn) {
            // 2) singleton node
            return sn.lookup(hc, key);
        } else {
            throw TrieMap.invalidElement(sub);
        }
    }

    @Nullable Result<V> remove(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final K key,
            final Object cond, final int lev, final INode<K, V> parent) {
        final int idx = hc >>> lev & 0x1f;
        final int flag = 1 << idx;
        if ((bitmap & flag) == 0) {
            return Result.empty();
        }

        final int pos = Integer.bitCount(bitmap & flag - 1);
        final var sub = array[pos];
        if (sub instanceof INode<K, V> in) {
            // renew if needed
            return startGen != in.gen && !renew(ct, parent, startGen)
                ? null : in.remove(ct, startGen, hc, key, cond, lev + LEVEL_BITS, parent);
        } else if (sub instanceof SNode<K, V> sn) {
            return remove(ct, parent, flag, pos, sn, key, hc, cond, lev);
        } else {
            throw TrieMap.invalidElement(sub);
        }
    }

    @Nullable Result<V> remove(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int flag, final int pos,
            final SNode<K, V> sn, final K key, final int hc, final Object cond, final int lev) {
        if (!sn.matches(hc, key) || cond != null && !cond.equals(sn.value())) {
            return Result.empty();
        }

        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len - 1);
        System.arraycopy(arr, 0, narr, 0, pos);
        System.arraycopy(arr, pos + 1, narr, pos, len - pos - 1);
        final var onlySN = onlySNode(narr, lev);
        return in.gcasWrite(onlySN != null ? onlySN.copyTombed(this) : new CNode<>(this, gen, bitmap ^ flag, narr), ct)
            ? sn.toResult() : null;
    }

    boolean renew(final TrieMap<K, V> ct, final INode<K, V> in, final Gen ngen) {
        return in.gcasWrite(renewed(ngen, ct), ct);
    }

    boolean insert(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos, final SNode<K, V> sn,
            final K key, final V val, final int hc, final int lev) {
        final CNode<K, V> next;
        if (!sn.matches(hc, key)) {
            final var rn = gen == in.gen ? this : renewed(gen, ct);
            next = rn.updatedAt(pos, new INode<>(in, sn, key, val, hc, lev), gen);
        } else {
            next = updatedAt(pos, key, val, hc, gen);
        }
        return in.gcasWrite(next, ct);
    }

    boolean insert(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos, final int flag, final K key,
            final V val, final int hc) {
        final var ngen = in.gen;
        final var rn = gen == ngen ? this : renewed(ngen, ct);
        return in.gcasWrite(rn.toInsertedAt(this, ngen, pos, flag, key, val, hc), ct);
    }

    @Nullable Result<V> insertIf(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos,
            final SNode<K, V> sn, final K key, final V val, final int hc, final Object cond, final int lev) {
        if (!sn.matches(hc, key)) {
            if (cond == null || cond == ABSENT) {
                final var ngen = in.gen;
                final var rn = gen == ngen ? this : renewed(ngen, ct);
                return in.gcasWrite(rn.toUpdatedAt(this, pos, new INode<>(in, sn, key, val, hc, lev), ngen), ct)
                    ? Result.empty() : null;
            }
            return Result.empty();
        }
        if (cond == ABSENT) {
            return sn.toResult();
        } else if (cond == null || cond == PRESENT || cond.equals(sn.value())) {
            return in.gcasWrite(updatedAt(pos, key, val, hc, gen), ct) ? sn.toResult() : null;
        }
        return Result.empty();
    }

    MainNode<K, V> toContracted(final CNode<K, V> prev, final int lev) {
        final var sn = onlySNode(array, lev);
        return sn == null ? new CNode<>(prev, gen, bitmap, array) : sn.copyTombed(prev);
    }

    static <K, V> MainNode<K, V> dual(final SNode<K, V> first, final K key, final V value, final int hc,
            final int initLev, final Gen gen) {
        final var second = new SNode<>(key, value, hc);
        final var fhc = first.hc();

        // recursion control
        final var bmps = new int[MAX_DEPTH];
        int len = 0;
        int lev = initLev;

        while (true) {
            MainNode<K, V> deepest;
            if (lev < HASH_BITS) {
                final int xidx = fhc >>> lev & 0x1f;
                final int yidx = hc >>> lev & 0x1f;
                final int bmp = 1 << xidx | 1 << yidx;
                if (xidx == yidx) {
                    // enter recursion: save bitmap and increment lev
                    bmps[len++] = bmp;
                    lev += LEVEL_BITS;
                    continue;
                }
                deepest = xidx < yidx ? new CNode<>(gen, bmp, first, second) : new CNode<>(gen, bmp, second, first);
            } else {
                deepest = new LNode<>(first, second);
            }

            while (len > 0) {
                // exit recursion: load bitmap and wrap deepest with a CNode
                deepest = new CNode<>(gen, bmps[--len], new INode<>(gen, deepest));
            }
            return deepest;
        }
    }

    @Override
    int trySize() {
        return csize;
    }

    @Override
    int size(final ImmutableTrieMap<?, ?> ct) {
        int sz;
        return (sz = csize) != NO_SIZE ? sz : (csize = computeSize(ct));
    }

    // lends itself towards being parallelizable by choosing
    // a random starting offset in the array
    // => if there are concurrent size computations, they start
    // at different positions, so they are more likely to
    // to be independent
    private int computeSize(final ImmutableTrieMap<?, ?> ct) {
        final int len = array.length;
        return switch (len) {
            case 0 -> 0;
            case 1 ->  elementSize(array[0], ct);
            default -> {
                final int offset = ThreadLocalRandom.current().nextInt(len);
                int sz = 0;
                for (int i = offset; i < len; ++i) {
                    sz += elementSize(array[i], ct);
                }
                for (int i = 0; i < offset; ++i) {
                    sz += elementSize(array[i], ct);
                }
                yield sz;
            }
        };
    }

    private static int elementSize(final Branch<?, ?> elem, final ImmutableTrieMap<?, ?> ct) {
        if (elem instanceof SNode) {
            return 1;
        } else if (elem instanceof INode<?, ?> inode) {
            return inode.size(ct);
        } else {
            throw TrieMap.invalidElement(elem);
        }
    }

    CNode<K, V> updatedAt(final int pos, final Branch<K, V> nn, final Gen ngen) {
        return toUpdatedAt(this, pos, nn, ngen);
    }

    private CNode<K, V> updatedAt(final int pos, final K key, final V val, final int hc, final Gen ngen) {
        return updatedAt(pos, new SNode<>(key, val, hc), ngen);
    }

    private CNode<K, V> toInsertedAt(final CNode<K, V> prev, final Gen ngen, final int pos, final int flag, final K key,
            final V value, final int hc) {
        final int len = array.length;
        final var narr = newArray(len + 1);
        System.arraycopy(array, 0, narr, 0, pos);
        narr[pos] = new SNode<>(key, value, hc);
        System.arraycopy(array, pos, narr, pos + 1, len - pos);
        return new CNode<>(prev, ngen, bitmap | flag, narr);
    }

    private CNode<K, V> toUpdatedAt(final CNode<K, V> prev, final int pos, final Branch<K, V> nn, final Gen ngen) {
        final int len = array.length;
        final var narr = newArray(len);
        System.arraycopy(array, 0, narr, 0, len);
        narr[pos] = nn;
        return new CNode<>(prev, ngen, bitmap, narr);
    }

    /**
     * Returns a copy of this cnode such that all the i-nodes below it are copied to the specified generation
     * {@code ngen}.
     */
    private CNode<K, V> renewed(final Gen ngen, final TrieMap<K, V> ct) {
        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len);
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<K, V> in ? in.copyToGen(ngen, ct) : tmp;
        }
        return new CNode<>(this, ngen, bitmap, narr);
    }

    // - if the branching factor is 1 for this CNode, and the child is a tombed SNode, returns its tombed version
    // - otherwise, if there is at least one non-null node below, returns the version of this node with at least some
    //   null-inodes removed (those existing when the op began)
    // - if there are only null-i-nodes below, returns null
    MainNode<K, V> toCompressed(final TrieMap<K, V> ct, final int lev, final Gen ngen) {
        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len);
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<K, V> in ? resurrect(in, ct) : tmp;
        }
        final var sn = onlySNode(narr, lev);
        return sn != null ? sn.copyTombed(this) : new CNode<>(this, ngen, bitmap, narr);
    }

    @Override
    public String toString() {
        return "CNode";
    }

    @SuppressWarnings("unchecked")
    private Branch<K, V>[] newArray(final int size) {
        return new Branch[size];
    }

    private @Nullable SNode<K, V> onlySNode(final Branch<K, V>[] arr, final int lev) {
        return arr.length == 1 && lev > 0 && arr[0] instanceof SNode<K, V> sn ? sn : null;
    }

    private static <K, V> Branch<K, V> resurrect(final INode<K, V> in, final TrieMap<K, V> ct) {
        return in.gcasReadNonNull(ct) instanceof TNode<K, V> tnode ? tnode.copyUntombed() : in;
    }
}
