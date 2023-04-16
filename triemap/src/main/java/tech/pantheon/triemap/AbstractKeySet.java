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

import static java.util.Objects.requireNonNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Spliterator;
import java.util.Spliterators;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Abstract base class for key set views of a TrieMap.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys
 */
abstract sealed class AbstractKeySet<K, M extends TrieMap<K, ?>> extends AbstractSet<K>
        permits ImmutableKeySet, MutableKeySet {
    final @NonNull M map;

    AbstractKeySet(final M map) {
        this.map = requireNonNull(map);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean addAll(final Collection<? extends K> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean contains(final Object o) {
        return map.containsKey(o);
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final Spliterator<K> spliterator() {
        // TODO: this is backed by an Iterator, we should be able to do better
        return Spliterators.spliterator(immutableIterator(), Long.MAX_VALUE, spliteratorCharacteristics());
    }

    @Override
    public abstract KeySetIterator<K> iterator();

    final @NonNull KeySetIterator<K> immutableIterator() {
        return new KeySetIterator<>(map.immutableIterator());
    }

    abstract int spliteratorCharacteristics();
}
