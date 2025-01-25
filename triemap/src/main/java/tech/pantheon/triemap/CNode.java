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
import static tech.pantheon.triemap.Result.RESTART;

import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

final class CNode<K, V> extends MainNode<K, V> {
    private static final Branch<?, ?>[] EMPTY_ARRAY = new Branch[0];

    final int bitmap;
    final Branch<K, V>[] array;
    private final Gen gen;

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

    static <K, V> MainNode<K, V> dual(final SNode<K, V> first, final @NonNull K key, final @NonNull V value,
            final int hc, final int initLev, final Gen gen) {
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

    @Nullable Object lookup(final TrieMap<K, V> ct, final Gen startGen, final int hc, final @NonNull K key,
            final int lev, final INode<K, V> parent) {
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
                ? in.lookup(ct, startGen, hc, key, lev + LEVEL_BITS, parent) : RESTART;
        } else if (sub instanceof SNode<K, V> sn) {
            // 2) singleton node
            return sn.lookup(hc, key);
        } else {
            throw invalidElement(sub);
        }
    }

    boolean insert(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final @NonNull K key,
            final @NonNull V val, final int lev, final INode<K, V> parent) {
        // 1) a multiway node
        final int idx = hc >>> lev & 0x1f;
        final int flag = 1 << idx;
        final int mask = flag - 1;
        final int pos = Integer.bitCount(bitmap & mask);

        if ((bitmap & flag) == 0) {
            return insert(ct, parent, pos, flag, key, val, hc);
        }

        // 1a) insert below
        final var cnAtPos = array[pos];
        if (cnAtPos instanceof INode<K, V> in) {
            // try to renew if needed and enter next level
            return (startGen == in.gen || renew(ct, parent, startGen))
                && in.insert(ct, startGen, hc, key, val, lev + LEVEL_BITS, parent);
        } else if (cnAtPos instanceof SNode<K, V> sn) {
            return insert(ct, parent, pos, sn, key, val, hc, lev);
        } else {
            throw invalidElement(cnAtPos);
        }
    }

    boolean insert(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos, final SNode<K, V> sn,
            final @NonNull K key, final @NonNull V val, final int hc, final int lev) {
        final CNode<K, V> next;
        if (!sn.matches(hc, key)) {
            final var rn = gen == in.gen ? this : renewed(ct, gen);
            next = rn.updatedAt(pos, new INode<>(in, sn, key, val, hc, lev), gen);
        } else {
            next = updatedAt(pos, key, val, hc, gen);
        }
        return in.gcasWrite(ct, next);
    }

    boolean insert(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos, final int flag,
            final @NonNull K key, final @NonNull V val, final int hc) {
        final var ngen = in.gen;
        final var rn = gen == ngen ? this : renewed(ct, ngen);
        return in.gcasWrite(ct, rn.toInsertedAt(this, ngen, pos, flag, key, val, hc));
    }

    @Nullable Object insertIf(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final @NonNull K key,
            final @NonNull V val, final @Nullable Object cond, final int lev, final INode<K, V> parent) {
        // 1) a multiway node
        final int idx = hc >>> lev & 0x1f;
        final int flag = 1 << idx;
        final int bmp = bitmap;
        final int mask = flag - 1;
        final int pos = Integer.bitCount(bmp & mask);

        if ((bmp & flag) == 0) {
            // not found
            return cond != null && cond != ABSENT || insert(ct, parent, pos, flag, key, val, hc) ? null : RESTART;
        }

        // 1a) insert below
        final var cnAtPos = array[pos];
        if (cnAtPos instanceof INode<K, V> in) {
            // enter next level
            return startGen != in.gen && !renew(ct, parent, startGen)
                ? RESTART : in.insertIf(ct, startGen, hc, key, val, cond, lev + LEVEL_BITS, parent);
        } else if (cnAtPos instanceof SNode<K, V> sn) {
            return insertIf(ct, parent, pos, sn, key, val, hc, cond, lev);
        } else {
            throw invalidElement(cnAtPos);
        }
    }

    private @Nullable Object insertIf(final MutableTrieMap<K, V> ct, final INode<K, V> in, final int pos,
            final SNode<K, V> sn, final @NonNull K key, final @NonNull V val, final int hc, final @Nullable Object cond,
            final int lev) {
        if (!sn.matches(hc, key)) {
            if (cond == null || cond == ABSENT) {
                final var ngen = in.gen;
                final var rn = gen == ngen ? this : renewed(ct, ngen);
                return in.gcasWrite(ct, rn.toUpdatedAt(this, pos, new INode<>(in, sn, key, val, hc, lev), ngen))
                    ? null : RESTART;
            }
            return null;
        }
        if (cond == ABSENT) {
            return sn.value();
        } else if (cond == null || cond == PRESENT || cond.equals(sn.value())) {
            return in.gcasWrite(ct, updatedAt(pos, key, val, hc, gen)) ? sn.value() : RESTART;
        }
        return null;
    }

    @Nullable Object remove(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final @NonNull K key,
            final @Nullable Object cond, final int lev, final INode<K, V> parent) {
        final int idx = hc >>> lev & 0x1f;
        final int flag = 1 << idx;
        if ((bitmap & flag) == 0) {
            return null;
        }

        final int pos = Integer.bitCount(bitmap & flag - 1);
        final var sub = array[pos];
        if (sub instanceof INode<K, V> in) {
            // renew if needed
            return startGen != in.gen && !renew(ct, parent, startGen)
                ? RESTART : in.remove(ct, startGen, hc, key, cond, lev + LEVEL_BITS, parent);
        } else if (sub instanceof SNode<K, V> sn) {
            if (!sn.matches(hc, key) || cond != null && !cond.equals(sn.value())) {
                return null;
            }
            return parent.gcasWrite(ct, toRemoved(ct, flag, pos, lev)) ? sn.value() : RESTART;
        } else {
            throw invalidElement(sub);
        }
    }

    private MainNode<K, V> toRemoved(final MutableTrieMap<K, V> ct, final int flag, final int pos, final int lev) {
        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len - 1);
        System.arraycopy(arr, 0, narr, 0, pos);
        System.arraycopy(arr, pos + 1, narr, pos, len - pos - 1);

        return toUpdated(gen, lev, narr, bitmap ^ flag);
    }

    // - if the branching factor is 1 for this CNode, and the child is a tombed SNode, returns its tombed version
    // - otherwise, if there is at least one non-null node below, returns the version of this node with at least some
    //   null-inodes removed (those existing when the op began)
    // - if there are only null-i-nodes below, returns null
    MainNode<K, V> toCompressed(final TrieMap<K, V> ct, final Gen ngen, final int lev) {
        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len);
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<K, V> in ? in.resurrect(ct) : tmp;
        }

        return toUpdated(ngen, lev, narr, bitmap);
    }

    MainNode<K, V> toContracted(final Gen ngen, final int pos, final TNode<K, V> tn, final int lev) {
        final int len = array.length;
        final var narr = newArray(len);
        System.arraycopy(array, 0, narr, 0, len);
        narr[pos] = new SNode<>(tn);

        return toUpdated(ngen, lev, narr, bitmap);
    }

    private MainNode<K, V> toUpdated(final Gen ngen, final int lev, final Branch<K, V>[] arr, final int bmp) {
        // Note: special-case for root, so we always have a ct.root.main is always a CNode
        return lev > 0 && arr.length == 1 && arr[0] instanceof SNode<K, V> sn
            ? new TNode<>(this, sn) : new CNode<>(this, ngen, bmp, arr);
    }

    // tries to gcasWrite() a copy of this CNode renewed to ngen
    private boolean renew(final TrieMap<K, V> ct, final INode<K, V> in, final Gen ngen) {
        return in.gcasWrite(ct, renewed(ct, ngen));
    }

    @Override
    int trySize() {
        return csize;
    }

    @Override
    int size(final ImmutableTrieMap<K, V> ct) {
        int sz;
        return (sz = csize) != NO_SIZE ? sz : (csize = computeSize(ct));
    }

    private int computeSize(final ImmutableTrieMap<K, V> ct) {
        final int len = array.length;
        return switch (len) {
            case 0 -> 0;
            case 1 -> array[0].elementSize(ct);
            default -> computeSize(ct, array, len);
        };
    }

    // Lends itself towards being parallelizable by choosing a random starting offset in the array: if there are
    // concurrent size computations, they start at different positions, so they are more likely to be independent
    private static <K, V> int computeSize(final ImmutableTrieMap<K, V> ct, final Branch<K, V>[] array, final int len) {
        // TODO: The other side of this argument is that array is 2-32 items long, i.e. on OpenJDK 21 on x64 the array
        //       ends up being 16 + (2-32) * (4/8) == 24-144 / 32-272 bytes each.
        //
        //       When traversing we do not dereference SNodes, but each INode either returns a cached value or goes off
        //       and branches (via a 16-byte object) branch to (eventually) this code in some other CNode. We also know
        //       we have at least 2 entries to traverse.
        //
        //       Taking into consideration a modern CPU, with:
        //         - 12 physical cores: 4 P-cores (2 threads each), 8 E-cores (1 thread each)
        //         - 64 byte cache line size
        //         - L1d
        //           - 48KiB L1d per P-core
        //           - 32KiB L1d per E-core
        //         - L2 unified
        //           - 1.25MiB per P-core
        //           - 2MiB for each 4 E-cores
        //         - L3 unified 12MiB
        //       it would seam that all things being optimal, each thread is using 24-32KiB L1d, 512-1024KiB L2 and
        //       about 769KiB of L3.
        //
        //       So three things:
        //         0) We really would like to prevent L1d bounces, so threads on different cores should be touching
        //            different cachelines. We are looking at traversing 3-5 linear cache lines.
        //         1) Would it make sense to inline the loops below, for example by counting odds and evens into
        //            separate variables, striding by 2 and then combining the two counters?
        //         2) On the other hand, doesn't JIT already take care of this? Is there something we can do better,
        //            like making sure the starting offset is aligned just by taking less random entropy?
        //
        // Note: len >= 2 is enforced by the sole caller
        final int offset = ThreadLocalRandom.current().nextInt(len);
        int sz = 0;
        for (int i = offset; i < len; ++i) {
            sz += array[i].elementSize(ct);
        }
        for (int i = 0; i < offset; ++i) {
            sz += array[i].elementSize(ct);
        }
        return sz;
    }

    private CNode<K, V> updatedAt(final int pos, final Branch<K, V> nn, final Gen ngen) {
        return toUpdatedAt(this, pos, nn, ngen);
    }

    private CNode<K, V> updatedAt(final int pos, final @NonNull K key, final @NonNull V val, final int hc,
            final Gen ngen) {
        return updatedAt(pos, new SNode<>(key, val, hc), ngen);
    }

    private CNode<K, V> toInsertedAt(final CNode<K, V> prev, final Gen ngen, final int pos, final int flag,
            final @NonNull K key, final @NonNull V value, final int hc) {
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
    private CNode<K, V> renewed(final TrieMap<K, V> ct, final Gen ngen) {
        final var arr = array;
        final int len = arr.length;
        final var narr = newArray(len);
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<K, V> in ? in.copyToGen(ct, ngen) : tmp;
        }
        return new CNode<>(this, ngen, bitmap, narr);
    }

    @Override
    public String toString() {
        return "CNode";
    }

    @SuppressWarnings("unchecked")
    private Branch<K, V>[] newArray(final int size) {
        return new Branch[size];
    }

    // Visible for testing
    static VerifyException invalidElement(final Branch<?, ?> elem) {
        throw new VerifyException("A CNode can contain only INodes and SNodes, not " + elem);
    }
}
