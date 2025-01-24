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

/**
 * A {@link MainNode}: one of {@link CNode}, {@link LNode} or {@link TNode}.
 */
abstract sealed class MainNode<K, V> extends INode.TryGcas<K, V> permits CNode, LNode, TNode {

    static final int NO_SIZE = -1;

    /**
     * Constructor for {@link CorLNode}, e.g. {@link CNode} and {@link LNode}, instances which are considered already
     * committed.
     */
    MainNode() {
        super();
    }

    /**
     * Constructor for instances which are succeeding a previous {@link CNode} node.
     */
    MainNode(final CNode<K, V> prev) {
        super(prev);
    }

    /**
     * Constructor for instances which are succeeding a previous {@link LNode} node.
     */
    MainNode(final LNode<K, V> prev) {
        super(prev);
    }

    /**
     * Return the number of entries in this node, or {@link #NO_SIZE} if it is not known.
     */
    abstract int trySize();

    /**
     * Return the number of entries in this node, traversing it if need be.
     *
     * @param ct TrieMap reference
     * @return The actual number of entries.
     */
    abstract int size(ImmutableTrieMap<K, V> ct);
}
