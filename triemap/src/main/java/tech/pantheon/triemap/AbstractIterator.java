/*
 * (C) Copyright 2017 PANTHEON.tech, s.r.o. and others.
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

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

/**
 * Abstract base class for iterators supporting {@link AbstractEntrySet} subclasses.
 *
 * @author Robert Varga
 *
 * @param <K> the type of entry keys
 * @param <V> the type of entry values
 */
abstract sealed class AbstractIterator<K, V> implements Iterator<Entry<K, V>>
        permits ImmutableIterator, MutableIterator {
    static {
        Constants.verifyMaxDepth();
    }

    private final BasicNode[][] nodeStack = new BasicNode[MAX_DEPTH][];
    private final int[] positionStack = new int[MAX_DEPTH];
    private final ImmutableTrieMap<K, V> map;

    private LNodeEntries<K, V> lnode;
    private EntryNode<K, V> current;
    private int depth = -1;

    AbstractIterator(final ImmutableTrieMap<K, V> map) {
        this.map = requireNonNull(map);
        readin(map.readRoot());
    }

    @Override
    public final boolean hasNext() {
        return current != null || lnode != null;
    }

    @Override
    public final Entry<K, V> next() {
        final Entry<K, V> entry;

        // Check LNode iterator first
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

        if (entry == null) {
            throw new NoSuchElementException();
        }

        return wrapEntry(entry);
    }

    /**
     * Wrap entry so it can be presented to the user.
     *
     * @param entry An immutable entry, guaranteed to be non-null
     * @return Wrapped entry, may not be null
     */
    abstract Entry<K, V> wrapEntry(Entry<K, V> entry);

    /**
     * Read the contents of an INode's main node.
     *
     * @param in INode to be read.
     */
    private void readin(final INode<K, V> in) {
        final var m = in.gcasRead(map);
        if (m instanceof CNode) {
            // Enter the next level
            final var cn = (CNode<K, V>) m;
            depth++;
            nodeStack[depth] = cn.array;
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