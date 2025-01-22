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

import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import org.eclipse.jdt.annotation.Nullable;

final class LNode<K, V> extends MainNode<K, V> {
    // Internally-linked single list of of entries
    private final LNodeEntries<K, V> entries;
    private final int size;

    private LNode(final LNode<K, V> prev, final LNodeEntries<K, V> entries, final int size) {
        super(prev);
        this.entries = entries;
        this.size = size;
    }

    private LNode(final LNode<K, V> prev, final K key, final V value) {
        this(prev, prev.entries.insert(key, value), prev.size + 1);
    }

    private LNode(final LNode<K, V> prev, final LNodeEntry<K, V> entry, final V value) {
        this(prev, prev.entries.replace(entry, value), prev.size);
    }

    LNode(final SNode<K, V> first, final SNode<K, V> second) {
        entries = LNodeEntries.map(first.key(), first.value(), second.key(), second.value());
        size = 2;
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

    LNodeEntry<K, V> get(final K key) {
        return entries.findEntry(key);
    }

    @Nullable V lookup(final K key) {
        final var entry = entries.findEntry(key);
        return entry != null ? entry.value() : null;
    }

    boolean insert(final INode<K, V> in, final K key, final V val, final TrieMap<K, V> ct) {
        final var entry = entries.findEntry(key);
        return in.gcasWrite(entry != null ? new LNode<>(this, entry, val) : new LNode<>(this, key, val), ct);
    }

    @Nullable Result<V> insertIf(final INode<K, V> in, final K key, final V val, final Object cond,
            final TrieMap<K, V> ct) {
        final var entry = entries.findEntry(key);
        if (entry == null) {
            return cond != null && cond != ABSENT || in.gcasWrite(new LNode<>(this, key, val), ct)
                ? Result.empty() : null;
        }
        if (cond == ABSENT) {
            return entry.toResult();
        } else if (cond == null || cond == PRESENT || cond.equals(entry.value())) {
            return in.gcasWrite(new LNode<>(this, entry, val), ct) ? entry.toResult() : null;
        }
        return Result.empty();
    }

    MainNode<K, V> removeChild(final LNodeEntry<K, V> entry, final int hc) {
        // While remove() can return null, that case will never happen here, as we are starting off with two entries
        // so we cannot observe a null return here.
        final var map = VerifyException.throwIfNull(entries.remove(entry));

        // If the returned LNode would have only one element, we turn it into a TNode, hence above null return from
        // remove() can never happen.
        return size != 2 ? new LNode<>(this, map, size - 1)
            // create it tombed so that it gets compressed on subsequent accesses
            : new TNode<>(this, map.key(), map.value(), hc);
    }
}
