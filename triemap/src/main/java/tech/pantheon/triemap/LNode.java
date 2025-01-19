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
    private final LNodeEntries<K, V> entries;
    private final int size;

    private LNode(final LNodeEntries<K, V> entries, final int size) {
        this.entries = entries;
        this.size = size;
    }

    LNode(final SNode<K, V> first, final SNode<K, V> second) {
        this(LNodeEntries.map(first.key(), first.value(), second.key(), second.value()), 2);
    }

    LNode<K, V> insertChild(final K key, final V value) {
        return new LNode<>(entries.insert(key, value), size + 1);
    }

    MainNode<K, V> removeChild(final LNodeEntry<K, V> entry, final int hc) {
        // While remove() can return null, that case will never happen here, as we are starting off with two entries
        // so we cannot observe a null return here.
        final var map = VerifyException.throwIfNull(entries.remove(entry));

        // If the returned LNode would have only one element, we turn it into a TNode, hence above null return from
        // remove() can never happen.
        return size != 2 ? new LNode<>(map, size - 1)
            // create it tombed so that it gets compressed on subsequent accesses
            : new TNode<>(map.key(), map.value(), hc);
    }

    MainNode<K, V> replaceChild(final LNodeEntry<K, V> entry, final V value) {
        return new LNode<>(entries.replace(entry, value), size);
    }

    LNodeEntry<K, V> get(final K key) {
        return entries.findEntry(key);
    }

    LNodeEntries<K, V> entries() {
        return entries;
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
