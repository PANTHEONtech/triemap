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

final class LNode<K, V> extends MainNode<K, V> {
    // Internally-linked single list of of entries
    final LNodeEntries<K, V> entries;
    final int size;

    LNode(final LNode<K, V> prev, final LNodeEntries<K, V> entries, final int size) {
        super(prev);
        this.entries = entries;
        this.size = size;
    }

    LNode(final SNode<K, V> first, final SNode<K, V> second) {
        entries = LNodeEntries.of(first.key(), first.value(), second.key(), second.value());
        size = 2;
    }

    @Override
    int trySize() {
        return size;
    }

    @Override
    int size(final ImmutableTrieMap<?, ?> ct) {
        return size;
    }
}
