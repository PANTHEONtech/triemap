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

import java.util.concurrent.ThreadLocalRandom;

final class CNode<K, V> extends MainNode<K, V> {
    private static final Branch[] EMPTY_ARRAY = new Branch[0];

    final int bitmap;
    final Branch[] array;
    final Gen gen;

    // Since concurrent computation should lead to same results we can update this field without any synchronization.
    private volatile int csize = NO_SIZE;

    private CNode(final Gen gen, final int bitmap, final Branch... array) {
        this.bitmap = bitmap;
        this.array = array;
        this.gen = gen;
    }

    CNode(final Gen gen) {
        this(gen, 0, EMPTY_ARRAY);
    }

    static <K, V> MainNode<K,V> dual(final SNode<K, V> snode, final K key, final V value, final int hc, final int lev,
            final Gen gen) {
        return dual(snode, snode.hc, new SNode<>(key, value, hc), hc, lev, gen);
    }

    private static <K, V> MainNode<K,V> dual(final SNode<K, V> first, final int firstHash, final SNode<K, V> second,
            final int secondHash, final int lev, final Gen gen) {
        if (lev >= HASH_BITS) {
            return new LNode<>(first.key, first.value, second.key, second.value);
        }

        final int xidx = firstHash >>> lev & 0x1f;
        final int yidx = secondHash >>> lev & 0x1f;
        final int bmp = 1 << xidx | 1 << yidx;

        if (xidx == yidx) {
            return new CNode<>(gen, bmp,
                new INode<>(gen, dual(first, firstHash, second, secondHash, lev + LEVEL_BITS, gen)));
        }

        return xidx < yidx ? new CNode<>(gen, bmp, first, second) : new CNode<>(gen, bmp, second, first);
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

    static VerifyException invalidElement(final Branch elem) {
        throw new VerifyException("A CNode can contain only INodes and SNodes, not " + elem);
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

    private static int elementSize(final Branch elem, final ImmutableTrieMap<?, ?> ct) {
        if (elem instanceof SNode) {
            return 1;
        } else if (elem instanceof INode<?, ?> inode) {
            return inode.size(ct);
        } else {
            throw invalidElement(elem);
        }
    }

    CNode<K, V> updatedAt(final int pos, final Branch nn, final Gen newGen) {
        final int len = array.length;
        final var narr = new Branch[len];
        System.arraycopy(array, 0, narr, 0, len);
        narr[pos] = nn;
        return new CNode<>(newGen, bitmap, narr);
    }

    CNode<K, V> removedAt(final int pos, final int flag, final Gen newGen) {
        final var arr = array;
        final int len = arr.length;
        final var narr = new Branch[len - 1];
        System.arraycopy(arr, 0, narr, 0, pos);
        System.arraycopy(arr, pos + 1, narr, pos, len - pos - 1);
        return new CNode<>(newGen, bitmap ^ flag, narr);
    }

    CNode<K, V> insertedAt(final int pos, final int flag, final Branch nn, final Gen newGen) {
        final int len = array.length;
        final var narr = new Branch[len + 1];
        System.arraycopy(array, 0, narr, 0, pos);
        narr[pos] = nn;
        System.arraycopy(array, pos, narr, pos + 1, len - pos);
        return new CNode<>(newGen, bitmap | flag, narr);
    }

    /**
     * Returns a copy of this cnode such that all the i-nodes below it are copied to the specified generation
     * {@code ngen}.
     */
    CNode<K, V> renewed(final Gen ngen, final TrieMap<K, V> ct) {
        final var arr = array;
        final int len = arr.length;
        final var narr = new Branch[len];
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<?, ?> in ? in.copyToGen(ngen, ct) : tmp;
        }
        return new CNode<>(ngen, bitmap, narr);
    }

    @SuppressWarnings("unchecked")
    MainNode<K, V> toContracted(final int lev) {
        return array.length == 1 && lev > 0 && array[0] instanceof SNode<?, ?> sn ? ((SNode<K, V>) sn).copyTombed()
            : this;
    }

    // - if the branching factor is 1 for this CNode, and the child is a tombed SNode, returns its tombed version
    // - otherwise, if there is at least one non-null node below, returns the version of this node with at least some
    //   null-inodes removed (those existing when the op began)
    // - if there are only null-i-nodes below, returns null
    MainNode<K, V> toCompressed(final TrieMap<?, ?> ct, final int lev, final Gen ngen) {
        final var arr = array;
        final int len = arr.length;
        final var narr = new Branch[len];
        for (int i = 0; i < len; i++) {
            final var tmp = arr[i];
            narr[i] = tmp instanceof INode<?, ?> in ? resurrect(in, ct) : tmp;
        }
        return new CNode<K, V>(ngen, bitmap, narr).toContracted(lev);
    }

    private static Branch resurrect(final INode<?, ?> in, final TrieMap<?, ?> ct) {
        return in.gcasReadNonNull(ct) instanceof TNode<?, ?> tnode ? tnode.copyUntombed() : in;
    }

    @Override
    public String toString() {
        return "CNode";
    }
}
