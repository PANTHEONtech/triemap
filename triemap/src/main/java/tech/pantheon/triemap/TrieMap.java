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
import static tech.pantheon.triemap.LookupResult.RESTART;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * This is a port of Scala's TrieMap class from the Scala Collections library. This implementation does not support
 * null keys nor null values.
 *
 * @author Aleksandar Prokopec (original Scala implementation)
 * @author Roman Levenstein (original Java 6 port)
 * @author Robert Varga
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public abstract sealed class TrieMap<K, V> implements ConcurrentMap<K,V>, Serializable
        permits ImmutableTrieMap, MutableTrieMap {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private transient AbstractEntrySet<K, V, ?> entrySet;
    private transient AbstractKeySet<K, ?> keySet;
    private transient AbstractCollection<V> values;

    TrieMap() {
        // Hidden on purpose
    }

    public static <K, V> MutableTrieMap<K, V> create() {
        return new MutableTrieMap<>();
    }

    /**
     * Returns a snapshot of this TrieMap. This operation is lock-free and
     * linearizable. Modification operations on this Map and the returned one
     * are isolated from each other.
     *
     * <p>
     * The snapshot is lazily updated - the first time some branch in the
     * snapshot or this TrieMap are accessed, they are rewritten. This means
     * that the work of rebuilding both the snapshot and this TrieMap is
     * distributed across all the threads doing updates or accesses subsequent
     * to the snapshot creation.
     *
     * @return A read-write TrieMap containing the contents of this map.
     */
    public abstract MutableTrieMap<K, V> mutableSnapshot();

    /**
     * Returns a read-only snapshot of this TrieMap. This operation is lock-free
     * and linearizable.
     *
     * <p>
     * The snapshot is lazily updated - the first time some branch of this
     * TrieMap are accessed, it is rewritten. The work of creating the snapshot
     * is thus distributed across subsequent updates and accesses on this
     * TrieMap by all threads. Note that the snapshot itself is never rewritten
     * unlike when calling {@link #mutableSnapshot()}, but the obtained snapshot
     * cannot be modified.
     *
     * <p>
     * This method is used by other methods such as `size` and `iterator`.
     *
     * @return A read-only TrieMap containing the contents of this map.
     */
    public abstract ImmutableTrieMap<K, V> immutableSnapshot();

    @Override
    public final boolean containsKey(final Object key) {
        return get(key) != null;
    }

    @Override
    public final boolean containsValue(final Object value) {
        Iterator<Entry<K,V>> iterator = entrySet().iterator();
        if (value == null) {
            while (iterator.hasNext()) {
                Entry<K,V> entry = iterator.next();
                if (entry.getValue() == null) {
                    return true;
                }
            }
        } else {
            while (iterator.hasNext()) {
                Entry<K,V> entry = iterator.next();
                if (value.equals(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public final Set<Entry<K, V>> entrySet() {
        final AbstractEntrySet<K, V, ?> ret;
        return (ret = entrySet) != null ? ret : (entrySet = createEntrySet());
    }

    @Override
    public final Set<K> keySet() {
        final AbstractKeySet<K, ?> ret;
        return (ret = keySet) != null ? ret : (keySet = createKeySet());
    }

    @Override
    public Collection<V> values() {
        final AbstractCollection<V> ret;
        return (ret = values) != null ? ret : (values = createValues());
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object anObject);

    @Override
    public abstract String toString();

    private AbstractCollection<V> createValues() {
        return new AbstractCollection<V>() {
            public Iterator<V> iterator() {
                AbstractEntrySet<K, V, ?> abstractEntrySet = (AbstractEntrySet<K, V, ?>)TrieMap.this.entrySet();
                return new ValuesIterator<V>((AbstractIterator<K, V>)abstractEntrySet.iterator());
            }

            public int size() {
                return TrieMap.this.size();
            }

            public boolean isEmpty() {
                return TrieMap.this.isEmpty();
            }

            public void clear() {
                TrieMap.this.clear();
            }

            public boolean contains(Object object) {
                return TrieMap.this.containsValue(object);
            }
        };

    }

    @Override
    public final V get(final Object key) {
        @SuppressWarnings("unchecked")
        final var k = (K) requireNonNull(key);
        return lookuphc(k, computeHash(k));
    }

    @Override
    public abstract void clear();

    @Override
    public abstract V put(K key, V value);

    @Override
    public abstract V putIfAbsent(K key, V value);

    @Override
    public abstract V remove(Object key);

    @Override
    public abstract boolean remove(Object key, Object value);

    @Override
    public abstract boolean replace(K key, V oldValue, V newValue);

    @Override
    public abstract V replace(K key, V value);

    @Override
    public abstract int size();

    /* internal methods implemented by subclasses */

    abstract AbstractEntrySet<K, V, ?> createEntrySet();

    abstract AbstractKeySet<K, ?> createKeySet();

    abstract boolean isReadOnly();

    abstract INode<K, V> rdcssReadRoot(boolean abort);

    /**
     * Return an iterator over a TrieMap.
     *
     * <p>
     * If this is a read-only snapshot, it would return a read-only iterator.
     *
     * <p>
     * If it is the original TrieMap or a non-readonly snapshot, it would return
     * an iterator that would allow for updates.
     *
     * @return An iterator.
     */
    abstract AbstractIterator<K, V> iterator();

    /* internal methods provided for subclasses */

    /**
     * Return an iterator over a TrieMap. This is a read-only iterator.
     *
     * @return A read-only iterator.
     */
    final ImmutableIterator<K, V> immutableIterator() {
        return new ImmutableIterator<>(immutableSnapshot());
    }

    static final int computeHash(final Object key) {
        int hash = key.hashCode();

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        hash ^= hash >>> 20 ^ hash >>> 12;
        hash ^= hash >>> 7 ^ hash >>> 4;
        return hash;
    }

    @java.io.Serial
    final Object writeReplace() {
        return new SerializationProxy(immutableSnapshot(), isReadOnly());
    }

    /* package-protected utility methods */

    final INode<K, V> readRoot() {
        return rdcssReadRoot(false);
    }

    // FIXME: abort = false by default
    final INode<K, V> readRoot(final boolean abort) {
        return rdcssReadRoot(abort);
    }

    /* private implementation methods */

    @SuppressWarnings("unchecked")
    private V lookuphc(final K key, final int hc) {
        Object res;
        do {
            // Keep looping as long as RESTART is being indicated
            res = readRoot().recLookup(key, hc, 0, null, this);
        } while (res == RESTART);

        return (V) res;
    }
}
