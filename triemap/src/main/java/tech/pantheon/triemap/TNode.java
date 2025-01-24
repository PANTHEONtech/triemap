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

import org.eclipse.jdt.annotation.NonNull;

final class TNode<K, V> extends MainNode<K, V> implements EntryNode<K, V> {
    final @NonNull K key;
    final @NonNull V value;
    final int hc;

    // Visible for testing
    TNode(final CNode<K, V> prev, final @NonNull K key, final @NonNull V value, final int hc) {
        super(prev);
        this.key = key;
        this.value = value;
        this.hc = hc;
    }

    TNode(final CNode<K, V> prev, final SNode<K, V> sn) {
        this(prev, sn.key(), sn.value(), sn.hc());
    }

    TNode(final LNode<K, V> prev, final @NonNull K key, final @NonNull V value, final int hc) {
        super(prev);
        this.key = key;
        this.value = value;
        this.hc = hc;
    }


    @Override
    public K key() {
        return key;
    }

    @Override
    public V value() {
        return value;
    }

    @Override
    int trySize() {
        return 1;
    }

    @Override
    int size(final ImmutableTrieMap<K, V> ct) {
        return 1;
    }

    @Override
    public int hashCode() {
        return AbstractEntry.hashCode(key, value);
    }

    @Override
    public boolean equals(final Object obj) {
        return AbstractEntry.equals(obj, key, value);
    }

    @Override
    public String toString() {
        return AbstractEntry.toString(key, value);
    }
}
