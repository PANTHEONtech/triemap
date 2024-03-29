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

import static java.util.Objects.requireNonNull;

import java.util.AbstractSet;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Abstract base class for implementing {@link TrieMap} entry sets.
 *
 * @author Robert Varga
 *
 * @param <K> the type of entry keys
 * @param <V> the type of entry values
 */
abstract sealed class AbstractEntrySet<K, V, M extends TrieMap<K, V>> extends AbstractSet<Entry<K, V>>
        permits ImmutableEntrySet, MutableEntrySet {
    final @NonNull M map;

    AbstractEntrySet(final M map) {
        this.map = requireNonNull(map);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean contains(final Object o) {
        if (!(o instanceof Entry<?, ?> entry)) {
            return false;
        }

        final var key = entry.getKey();
        if (key == null) {
            return false;
        }
        final var value = entry.getValue();
        return value != null && value.equals(map.get(key));
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final Spliterator<Entry<K, V>> spliterator() {
        // TODO: this is backed by an Iterator, we should be able to do better
        return Spliterators.spliterator(map.iterator(), Long.MAX_VALUE, characteristics());
    }

    abstract int characteristics();
}
