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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An immutable TrieMap.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public final class ImmutableTrieMap<K, V> extends TrieMap<K, V> {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Handled through writeReplace")
    private final transient INode<K, V> root;

    ImmutableTrieMap(final INode<K, V> root) {
        this.root = requireNonNull(root);
    }

    @Override
    public void clear() {
        throw unsupported();
    }

    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw unsupported();
    }

    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        throw unsupported();
    }

    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw unsupported();
    }

    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw unsupported();
    }

    @Override
    public V put(final K key, final V value) {
        throw unsupported();
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public void putAll(final Map<? extends K, ? extends V> m) {
        throw unsupported();
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        throw unsupported();
    }

    @Override
    public V remove(final Object key) {
        throw unsupported();
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        throw unsupported();
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        throw unsupported();
    }

    @Override
    public V replace(final K key, final V value) {
        throw unsupported();
    }

    @Override
    public int size() {
        return root.elementSize(this);
    }

    @Override
    public MutableTrieMap<K, V> mutableSnapshot() {
        return new MutableTrieMap<>(root.copyToGen(this, new Gen()));
    }

    @Override
    public ImmutableTrieMap<K, V> immutableSnapshot() {
        return this;
    }

    @Override
    ImmutableEntrySet<K, V> createEntrySet() {
        return new ImmutableEntrySet<>(this);
    }

    @Override
    ImmutableKeySet<K> createKeySet() {
        return new ImmutableKeySet<>(this);
    }

    @Override
    boolean isReadOnly() {
        return true;
    }

    @Override
    ImmutableIterator<K, V> iterator() {
        return immutableIterator();
    }

    @Override
    INode<K, V> rdcssReadRoot(final boolean abort) {
        return root;
    }

    static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Attempted to modify a read-only view");
    }
}
