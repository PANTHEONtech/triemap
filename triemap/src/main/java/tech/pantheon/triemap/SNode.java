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

final class SNode<K, V> extends BasicNode implements EntryNode<K, V> {
    final K key;
    final V value;
    final int hc;

    SNode(final K key, final V value, final int hc) {
        this.key = key;
        this.value = value;
        this.hc = hc;
    }

    TNode<K, V> copyTombed() {
        return new TNode<>(key, value, hc);
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @NonNull Result<V> toResult() {
        return new Result<>(value);
    }

    @Override
    public int hashCode() {
        return EntryUtil.entryHashCode(key, value);
    }

    @Override
    public boolean equals(final Object obj) {
        return EntryUtil.entryEquals(obj, key, value);
    }

    @Override
    public String toString() {
        return EntryUtil.entryToString(key, value);
    }
}
