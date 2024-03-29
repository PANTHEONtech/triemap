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

import java.util.Spliterator;

/**
 * A mutable view of a TrieMap's key set.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys
 */
final class MutableKeySet<K> extends AbstractKeySet<K, MutableTrieMap<K, ?>> {
    MutableKeySet(final MutableTrieMap<K, ?> map) {
        super(map);
    }

    @Override
    public KeySetIterator<K> iterator() {
        return new KeySetIterator<>(map.iterator());
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public boolean remove(final Object o) {
        return map.remove(o) != null;
    }

    @Override
    int spliteratorCharacteristics() {
        return Spliterator.DISTINCT | Spliterator.CONCURRENT | Spliterator.NONNULL;
    }
}
