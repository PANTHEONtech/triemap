/*
 * (C) Copyright 2023 PANTHEON.tech, s.r.o. and others.
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

import static java.util.Objects.requireNonNull;
import static tech.pantheon.triemap.Constants.MAX_DEPTH;

import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

final class EntrySetSpliterator<K, V, M extends TrieMap<K, V>> implements Spliterator<Entry<K, V>> {

    private final BasicNode[][] nodeStack = new BasicNode[MAX_DEPTH][];
    private final int[] positionStack = new int[MAX_DEPTH];
    private final M map;

    private LNodeEntries<K, V> lnode;
    private EntryNode<K, V> current;
    private int depth = -1;

    private final int characteristics;
    private final UnaryOperator<Entry<K, V>> entryWrapper;

    EntrySetSpliterator(final M map, final int characteristics, final UnaryOperator<Entry<K, V>> entryWrapper) {
        this.map = map;
        this.characteristics = characteristics;
        this.entryWrapper = entryWrapper;
        readin(map.readRoot());
    }

    @Override
    public boolean tryAdvance(Consumer<? super Entry<K, V>> consumer) {
        requireNonNull(consumer);
        final var entry = next();
        if (entry == null) {
            return false;
        }
        consumer.accept(entryWrapper.apply(entry));
        return true;
    }

    @Override
    public Spliterator<Entry<K, V>> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return 0;
    }

    @Override
    public int characteristics() {
        return characteristics;
    }

    private Entry<K, V> next() {
        final Entry<K, V> entry;
        if (lnode != null) {
            entry = lnode;
            lnode = lnode.next();
            if (lnode == null) {
                advance();
            }
        } else {
            entry = current;
            advance();
        }
        return entry;
    }

    private void readin(final INode<K, V> in) {
        final var m = in.gcasRead(map);
        if (m instanceof CNode) {
            // Enter the next level
            final var cn = (CNode<K, V>) m;
            nodeStack[++depth] = cn.array;
            positionStack[depth] = -1;
            advance();
        } else if (m instanceof TNode) {
            current = (TNode<K, V>) m;
        } else if (m instanceof LNode) {
            lnode = ((LNode<K, V>) m).entries();
        } else if (m == null) {
            current = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void advance() {
        if (depth >= 0) {
            int npos = positionStack[depth] + 1;
            if (npos < nodeStack[depth].length) {
                positionStack [depth] = npos;
                var elem = nodeStack[depth][npos];
                if (elem instanceof SNode) {
                    current = (SNode<K, V>) elem;
                } else if (elem instanceof INode) {
                    readin((INode<K, V>) elem);
                }
            } else {
                depth -= 1;
                advance();
            }
        } else {
            current = null;
        }
    }
}
