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
import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Optional;

/**
 * A mutable TrieMap.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public final class MutableTrieMap<K, V> extends TrieMap<K, V> {
    private static final long serialVersionUID = 1L;

    private static final VarHandle ROOT;

    static {
        try {
            ROOT = MethodHandles.lookup().findVarHandle(MutableTrieMap.class, "root", Object.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile Object root;

    MutableTrieMap(final Equivalence<? super K> equiv) {
        this(equiv, newRootNode());
    }

    MutableTrieMap(final Equivalence<? super K> equiv, final INode<K, V> root) {
        super(equiv);
        this.root = requireNonNull(root);
    }

    @Override
    public void clear() {
        INode<K, V> localRoot;
        do {
            localRoot = readRoot();
        } while (!rdcssRoot(localRoot, localRoot.gcasRead(this), newRootNode()));
    }

    @Override
    public V put(final K key, final V value) {
        final K k = requireNonNull(key);
        return toNullable(insertifhc(k, computeHash(k), requireNonNull(value), null));
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        final K k = requireNonNull(key);
        return toNullable(insertifhc(k, computeHash(k), requireNonNull(value), ABSENT));
    }

    @Override
    public V remove(final Object key) {
        @SuppressWarnings("unchecked")
        final K k = (K) requireNonNull(key);
        return toNullable(removehc(k, null, computeHash(k)));
    }

    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "API contract allows null value, but we do not")
    @Override
    public boolean remove(final Object key, final Object value) {
        @SuppressWarnings("unchecked")
        final K k = (K) requireNonNull(key);
        return removehc(k, requireNonNull(value), computeHash(k)).isPresent();
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        final K k = requireNonNull(key);
        return insertifhc(k, computeHash(k), requireNonNull(newValue), requireNonNull(oldValue)).isPresent();
    }

    @Override
    public V replace(final K key, final V value) {
        final K k = requireNonNull(key);
        return toNullable(insertifhc(k, computeHash(k), requireNonNull(value), PRESENT));
    }

    @Override
    public int size() {
        return immutableSnapshot().size();
    }

    private INode<K, V> snapshot() {
        INode<K, V> localRoot;
        do {
            localRoot = readRoot();
        } while (!rdcssRoot(localRoot, localRoot.gcasRead(this), localRoot.copyToGen(new Gen(), this)));

        return localRoot;
    }

    @Override
    public ImmutableTrieMap<K, V> immutableSnapshot() {
        return new ImmutableTrieMap<>(snapshot(), equiv());
    }

    @Override
    public MutableTrieMap<K, V> mutableSnapshot() {
        return new MutableTrieMap<>(equiv(), snapshot().copyToGen(new Gen(), this));
    }

    @Override
    MutableEntrySet<K, V> createEntrySet() {
        // FIXME: it would be nice to have a ReadWriteTrieMap with read-only iterator
        //        if (readOnlyEntrySet) return ImmutableEntrySet(this);
        return new MutableEntrySet<>(this);
    }

    @Override
    MutableKeySet<K> createKeySet() {
        return new MutableKeySet<>(this);
    }

    @Override
    MutableIterator<K, V> iterator() {
        return new MutableIterator<>(this);
    }

    @Override
    boolean isReadOnly() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    INode<K, V> rdcssReadRoot(final boolean abort) {
        final Object r = /* READ */ root;
        if (r instanceof INode) {
            return (INode<K, V>) r;
        }

        verifyRootDescriptor(r);
        return rdcssComplete(abort);
    }

    void add(final K key, final V value) {
        final K k = requireNonNull(key);
        inserthc(k, computeHash(k), requireNonNull(value));
    }

    private static <K,V> INode<K, V> newRootNode() {
        final Gen gen = new Gen();
        return new INode<>(gen, new CNode<>(gen));
    }

    private void inserthc(final K key, final int hc, final V value) {
        // TODO: this is called from serialization only, which means we should not be observing any races,
        //       hence we should not need to pass down the entire tree, just equality (I think).
        if (!readRoot().recInsert(key, value, hc, 0, null, this)) {
            throw new VerifyException("Concurrent modification during serialization of map " + this);
        }
    }

    private Optional<V> insertifhc(final K key, final int hc, final V value, final Object cond) {
        Optional<V> res;
        do {
            // Keep looping as long as we do not get a reply
            res = readRoot().recInsertIf(key, value, hc, cond, 0, null, this);
        } while (res == null);

        return res;
    }

    private Optional<V> removehc(final K key, final Object cond, final int hc) {
        Optional<V> res;
        do {
            // Keep looping as long as we do not get a reply
            res = readRoot().recRemove(key, cond, hc, 0, null, this);
        } while (res == null);

        return res;
    }

    private boolean casRoot(final Object ov, final Object nv) {
        return ROOT.compareAndSet(this, ov, nv);
    }

    private boolean rdcssRoot(final INode<K, V> ov, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
        final RDCSS_Descriptor<K, V> desc = new RDCSS_Descriptor<>(ov, expectedmain, nv);
        if (casRoot(ov, desc)) {
            rdcssComplete(false);
            return /* READ */desc.committed;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private INode<K, V> rdcssComplete(final boolean abort) {
        while (true) {
            final Object r = /* READ */ root;
            if (r instanceof INode) {
                return (INode<K, V>) r;
            }

            verifyRootDescriptor(r);
            final RDCSS_Descriptor<K, V> desc = (RDCSS_Descriptor<K, V>) r;
            final INode<K, V> ov = desc.old;
            final MainNode<K, V> exp = desc.expectedmain;
            final INode<K, V> nv = desc.nv;

            if (abort) {
                if (casRoot(desc, ov)) {
                    return ov;
                }

                // Tail recursion: return RDCSS_Complete(abort);
                continue;
            }

            final MainNode<K, V> oldmain = ov.gcasRead(this);
            if (oldmain == exp) {
                if (casRoot(desc, nv)) {
                    desc.committed = true;
                    return nv;
                }

                // Tail recursion: return RDCSS_Complete(abort);
                continue;
            }

            if (casRoot(desc, ov)) {
                return ov;
            }

            // Tail recursion: return RDCSS_Complete(abort);
        }
    }

    private static void verifyRootDescriptor(final Object obj) {
        if (!(obj instanceof RDCSS_Descriptor)) {
            throw new VerifyException("Unhandled root " + obj);
        }
    }

    @SuppressWarnings("checkstyle:typeName")
    private static final class RDCSS_Descriptor<K, V> {
        final INode<K, V> old;
        final MainNode<K, V> expectedmain;
        final INode<K, V> nv;

        volatile boolean committed = false;

        RDCSS_Descriptor(final INode<K, V> old, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
            this.old = old;
            this.expectedmain = expectedmain;
            this.nv = nv;
        }
    }
}
