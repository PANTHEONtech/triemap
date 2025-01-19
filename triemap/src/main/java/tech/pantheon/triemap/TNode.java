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

final class TNode<K, V> extends MainNode<K, V> implements DefaultEntry<K, V> {
    final K key;
    final V value;
    final int hc;

    TNode(final K key, final V value, final int hc) {
        this.key = key;
        this.value = value;
        this.hc = hc;
    }

    SNode<K, V> copyUntombed() {
        return new SNode<>(key, value, hc);
    }

    @Override
    int trySize() {
        return 1;
    }

    @Override
    int size(final ImmutableTrieMap<?, ?> ct) {
        return 1;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
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
