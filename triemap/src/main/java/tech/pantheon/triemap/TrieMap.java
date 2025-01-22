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
import static tech.pantheon.triemap.Constants.LEVEL_BITS;

import java.io.Serializable;
import java.util.AbstractMap;
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
public abstract sealed class TrieMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K,V>, Serializable
        permits ImmutableTrieMap, MutableTrieMap {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    // Virtual result for lookup methods indicating that the lookup needs to be restarted. This is a faster version
    // of throwing a checked exception to control restart.
    private static final Object RESTART = new Object();

    private transient AbstractEntrySet<K, V, ?> entrySet;
    // Note: AbstractMap.keySet is something we do not have access to. At some point we should just not subclass
    //       AbstractMap and lower our memory footprint.
    private transient AbstractKeySet<K, ?> theKeySet;

    TrieMap() {
        // Hidden on purpose
    }

    /**
     * Create a new {@link MutableTrieMap}.
     *
     * @param <K> key type
     * @param <V> value type
     * @return A new {@link MutableTrieMap}.
     */
    public static <K, V> MutableTrieMap<K, V> create() {
        return new MutableTrieMap<>();
    }

    /**
     * Returns a snapshot of this TrieMap. This operation is lock-free and linearizable. Modification operations on
     * this Map and the returned one are isolated from each other.
     *
     * <p>The snapshot is lazily updated - the first time some branch in the snapshot or this TrieMap are accessed,
     * they are rewritten. This means that the work of rebuilding both the snapshot and this TrieMap is distributed
     * across all the threads doing updates or accesses subsequent to the snapshot creation.
     *
     * @return A read-write TrieMap containing the contents of this map.
     */
    public abstract MutableTrieMap<K, V> mutableSnapshot();

    /**
     * Returns a read-only snapshot of this TrieMap. This operation is lock-free and linearizable.
     *
     * <p>The snapshot is lazily updated - the first time some branch of this TrieMap are accessed, it is rewritten.
     * The work of creating the snapshot is thus distributed across subsequent updates and accesses on this TrieMap
     * by all threads. Note that the snapshot itself is never rewritten unlike when calling {@link #mutableSnapshot()},
     * but the obtained snapshot cannot be modified.
     *
     * <p>This method is used by other methods such as `size` and `iterator`.
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
        return super.containsValue(requireNonNull(value));
    }

    @Override
    public final Set<Entry<K, V>> entrySet() {
        final AbstractEntrySet<K, V, ?> ret;
        return (ret = entrySet) != null ? ret : (entrySet = createEntrySet());
    }

    @Override
    public final Set<K> keySet() {
        final AbstractKeySet<K, ?> ret;
        return (ret = theKeySet) != null ? ret : (theKeySet = createKeySet());
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
     * <p>If this is a read-only snapshot, it would return a read-only iterator.
     *
     * <p>If it is the original TrieMap or a non-readonly snapshot, it would return an iterator that would allow for
     * updates.
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

    /**
     * Replace this set with its {@link SerializationProxy}.
     *
     * @return {@link SerializationProxy}
     */
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
            res = recLookup(readRoot(), key, hc);
        } while (res == RESTART);

        return (V) res;
    }

    /**
     * Looks up the value associated with the key.
     *
     * @param first root {@link INode}
     * @param key the key
     * @param the hash code
     * @return null if no value has been found, RESTART if the operation was not successful, or any other value
     *              otherwise
     */
    private Object recLookup(final INode<K, V> first, final K key, final int hc) {
        final var startGen = first.gen;
        INode<K, V> parent = null;
        var current = first;
        int lev = 0;

        while (true) {
            final var m = current.gcasRead(this);

            if (m instanceof CNode<K, V> cn) {
                // 1) a multinode
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;

                final int bmp = cn.bitmap;
                if ((bmp & flag) == 0) {
                    // 1a) bitmap shows no binding
                    return null;
                }

                // 1b) bitmap contains a value - descend
                final int pos = bmp == 0xffffffff ? idx : Integer.bitCount(bmp & flag - 1);
                final var sub = cn.array[pos];
                if (sub instanceof INode<?, ?> inode) {
                    @SuppressWarnings("unchecked")
                    final var in = (INode<K, V>) inode;
                    // try to renew if needed
                    if (!isReadOnly() && startGen != in.gen && !current.gcasWrite(cn.renewed(startGen, this), this)) {
                        return RESTART;
                    }

                    // enter next level
                    parent = current;
                    current = in;
                    lev += LEVEL_BITS;
                } else if (sub instanceof SNode<?, ?> snode) {
                    // 2) singleton node
                    return ((SNode<K, V>) snode).lookup(hc, key);
                } else {
                    throw invalidElement(sub);
                }
            } else if (m instanceof TNode<K, V> tn) {
                // 3) non-live node
                if (isReadOnly()) {
                    // read-only side does not clean up
                    return tn.hc == hc && key.equals(tn.key) ? tn.value : null;
                }
                // read-write: perform some clean up and restart
                clean(current, parent, lev - LEVEL_BITS);
                return RESTART;
            } else if (m instanceof LNode<K, V> ln) {
                // 5) an l-node
                return ln.entries.lookup(key);
            } else {
                throw invalidElement(m);
            }
        }
    }

    final void clean(final INode<K, V> current, final INode<K, V> parent, final int lev) {
        if (parent.gcasRead(this) instanceof CNode<K, V> cn) {
            parent.gcasWrite(cn.toCompressed(this, lev  - LEVEL_BITS, current.gen), this);
        }
    }

    static final VerifyException invalidElement(final Branch elem) {
        throw new VerifyException("A CNode can contain only INodes and SNodes, not " + elem);
    }

    static final VerifyException invalidElement(final MainNode<?, ?> elem) {
        throw new VerifyException("An INode can host only a CNode, a TNode or an LNode, not " + elem);
    }
}
