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

/**
 * Specialized immutable iterator for use with {@link ImmutableEntrySet}.
 *
 * @author Robert Varga
 *
 * @param <K> the type of entry keys
 * @param <V> the type of entry values
 */
final class MutableIterator<K, V> extends AbstractIterator<K, V> {
    private final MutableTrieMap<K, V> mutable;

    private MutableEntry<K, V> lastReturned;

    MutableIterator(final MutableTrieMap<K, V> map) {
        super(map.immutableSnapshot());
        mutable = map;
    }

    @Override
    public void remove() {
        if (lastReturned == null) {
            throw new IllegalStateException();
        }
        mutable.remove(lastReturned.key());
        lastReturned = null;
    }

    @Override
    MutableEntry<K, V> wrapEntry(final DefaultEntry<K, V> entry) {
        final var ret = new MutableEntry<>(mutable, entry);
        lastReturned = ret;
        return ret;
    }

    /**
     * A mutable view of an entry in the map. Since the backing map is concurrent, its {@link #getValue()} and
     * {@link #setValue(Object)} methods cannot guarantee consistency with the base map and may produce surprising
     * results when the map is concurrently modified, either directly or via another entry/iterator.
     *
     * <p>The behavior is similar to what Java 8's ConcurrentHashMap does, which is probably the most consistent
     * handling of this case without requiring expensive and revalidation.
     */
    static final class MutableEntry<K, V> extends AbstractEntry<K, V> {
        private final MutableTrieMap<K, V> map;
        private final DefaultEntry<K, V> delegate;

        @SuppressWarnings("null")
        private V newValue = null;

        private MutableEntry(final MutableTrieMap<K, V> map, final DefaultEntry<K, V> delegate) {
            this.map = map;
            this.delegate = delegate;
        }

        @Override
        public K key() {
            return delegate.key();
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec
         *     This implementation returns the most uptodate value we have observed via this entry. It does not reflect
         *     concurrent modifications, nor does it throw {@link IllegalStateException} if the entry is removed.
         */
        @Override
        public V value() {
            return newValue != null ? newValue : delegate.value();
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec
         *     This implementation returns the most uptodate value we have observed via this entry. It does not reflect
         *     concurrent modifications, nor does it throw {@link IllegalStateException} if the entry is removed.
         */
        @Override
        public V setValue(final V value) {
            final var ret = value();
            map.put(key(), value);
            newValue = value;
            return ret;
        }
    }
}
