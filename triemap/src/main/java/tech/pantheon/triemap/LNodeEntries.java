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
 * Similar to Scala&apos;s ListMap, this is a single-linked list of set of map entries. Aside from the java.util.Set
 * contract, this class fulfills the requirements for an immutable map entryset.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
abstract class LNodeEntries<K, V> extends LNodeEntry<K, V> {
    // Visible for testing
    static final class Single<K, V> extends LNodeEntries<K, V> {
        Single(final K key, final V value) {
            super(key, value);
        }

        @Override
        LNodeEntries<K, V> next() {
            return null;
        }
    }

    private static final class Multiple<K, V> extends LNodeEntries<K, V> {
        // Modified during remove only, otherwise final
        LNodeEntries<K, V> next;

        // Used in remove() only
        Multiple(final LNodeEntries<K, V> entry) {
            this(entry.getKey(), entry.getValue(), null);
        }

        Multiple(final K key, final V value, final LNodeEntries<K, V> next) {
            super(key, value);
            this.next = next;
        }

        @Override
        LNodeEntries<K, V> next() {
            return next;
        }
    }

    LNodeEntries(final K key, final V value) {
        super(key, value);
    }

    static <K,V> LNodeEntries<K, V> map(final K k1, final V v1, final K k2, final V v2) {
        return new Multiple<>(k1, v1, new Single<>(k2, v2));
    }

    /**
     * Return the remainder of this list. Useful for implementing Iterator-like contract. Null indicates there are no
     * more entries.
     *
     * @return Remainder of this list, or null if nothing remains
     */
    abstract LNodeEntries<K, V> next();

    final LNodeEntry<K, V> findEntry(final K key) {
        // We do not perform recursion on purpose here, so we do not run out of stack if the key hashing fails.
        var entry = this;
        do {
            if (key.equals(entry.getKey())) {
                return entry;
            }

            entry = entry.next();
        } while (entry != null);

        return null;
    }

    final LNodeEntries<K,V> insert(final K key, final V value) {
        return new Multiple<>(key, value, this);
    }

    final LNodeEntries<K, V> replace(final LNodeEntry<K, V> entry, final V value) {
        final LNodeEntries<K, V> removed;
        return (removed = remove(entry)) == null ? new Single<>(entry.getKey(), value)
                : new Multiple<>(entry.getKey(), value, removed);
    }

    final LNodeEntries<K, V> remove(final LNodeEntry<K, V> entry) {
        if (entry == this) {
            return next();
        }

        // This will result in a list with a long tail, i.e last entry storing explicit null. Overhead is amortized
        // against the number of entries. We do not retain chains shorter than two, so the worst-case overhead is
        // half-a-reference for an entry.
        final var ret = new Multiple<>(this);

        var last = ret;
        var cur = next();
        while (cur != null) {
            // We cannot use equals() here, as it is wired to key equality and we must never compare entries based on
            // that property. This method is intended to remove a known reference, so identity is what we want.
            if (entry == cur) {
                last.next = cur.next();
                return ret;
            }

            final var tmp = new Multiple<>(cur);
            last.next = tmp;
            last = tmp;
            cur = cur.next();
        }

        throw new VerifyException("Failed to find entry " + entry);
    }
}
