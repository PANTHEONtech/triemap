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
import org.eclipse.jdt.annotation.NonNull;

/**
 * A mutable TrieMap.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public final class MutableTrieMap<K, V> extends TrieMap<K, V> {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private static final VarHandle ROOT;

    static {
        try {
            ROOT = MethodHandles.lookup().findVarHandle(MutableTrieMap.class, "root", Root.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Either an INode or a RDCSS_Descriptor
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Handled through writeReplace()")
    private transient volatile Root root;

    MutableTrieMap() {
        this(newRootNode());
    }

    MutableTrieMap(final INode<K, V> root) {
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
        final var k = requireNonNull(key);
        return insertifhc(k, computeHash(k), requireNonNull(value), null).orNull();
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        final var k = requireNonNull(key);
        return insertifhc(k, computeHash(k), requireNonNull(value), ABSENT).orNull();
    }

    @Override
    public V remove(final Object key) {
        @SuppressWarnings("unchecked")
        final var k = (K) requireNonNull(key);
        return removehc(k, null, computeHash(k)).orNull();
    }

    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "API contract allows null value, but we do not")
    @Override
    public boolean remove(final Object key, final Object value) {
        @SuppressWarnings("unchecked")
        final var k = (K) requireNonNull(key);
        return removehc(k, requireNonNull(value), computeHash(k)).isPresent();
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        final var k = requireNonNull(key);
        return insertifhc(k, computeHash(k), requireNonNull(newValue), requireNonNull(oldValue)).isPresent();
    }

    @Override
    public V replace(final K key, final V value) {
        final var k = requireNonNull(key);
        return insertifhc(k, computeHash(k), requireNonNull(value), PRESENT).orNull();
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
        return new ImmutableTrieMap<>(snapshot());
    }

    @Override
    public MutableTrieMap<K, V> mutableSnapshot() {
        return new MutableTrieMap<>(snapshot().copyToGen(new Gen(), this));
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
        final var r = /* READ */ root;
        final INode<?, ?> ret;
        if (r instanceof INode<?, ?> inode) {
            ret = inode;
        } else if (r instanceof RDCSS_Descriptor<?, ?> desc) {
            ret = rdcssComplete(desc, abort);
        } else {
            throw new VerifyException("Unhandled root " + r);
        }
        return (INode<K, V>) ret;
    }

    void add(final K key, final V value) {
        final K k = requireNonNull(key);
        inserthc(k, computeHash(k), requireNonNull(value));
    }

    private static <K, V> INode<K, V> newRootNode() {
        final var gen = new Gen();
        return new INode<>(gen, new CNode<>(gen));
    }

    private void inserthc(final K key, final int hc, final V value) {
        // TODO: this is called from serialization only, which means we should not be observing any races,
        //       hence we should not need to pass down the entire tree, just equality (I think).
        if (!readRoot().recInsert(key, value, hc, 0, null, this)) {
            throw new VerifyException("Concurrent modification during serialization of map " + this);
        }
    }

    private @NonNull Result<V> insertifhc(final K key, final int hc, final V value, final Object cond) {
        Result<V> res;
        do {
            // Keep looping as long as we do not get a reply
            res = readRoot().recInsertIf(key, value, hc, cond, 0, null, this);
        } while (res == null);

        return res;
    }

    private @NonNull Result<V> removehc(final K key, final Object cond, final int hc) {
        Result<V> res;
        do {
            // Keep looping as long as we do not get a reply
            res = readRoot().recRemove(key, cond, hc, 0, null, this);
        } while (res == null);

        return res;
    }

    private Root casRoot(final Root ov, final Root nv) {
        return (Root) ROOT.compareAndExchange(this, ov, nv);
    }

    private boolean rdcssRoot(final INode<K, V> ov, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
        final var desc = new RDCSS_Descriptor<>(ov, expectedmain, nv);
        final var witness = casRoot(ov, desc);
        if (witness == ov) {
            rdcssComplete(desc, false);
            return desc.readCommitted();
        }

        return false;
    }

    private INode<?, ?> rdcssComplete(final RDCSS_Descriptor<?, ?> initial, final boolean abort) {
        var desc = initial;

        while (true) {
            final Root next;

            final var ov = desc.old;
            if (abort || ov.gcasRead(this) != desc.expectedmain) {
                next = casRoot(desc, ov);
                if (next == desc) {
                    return ov;
                }
            } else {
                final var nv = desc.nv;
                next = casRoot(desc, nv);
                if (next == desc) {
                    desc.setCommitted();
                    return nv;
                }
            }

            if (next instanceof INode<?, ?> inode) {
                return inode;
            } else if (next instanceof RDCSS_Descriptor<?, ?> nextDesc) {
                // Tail recursion: return rdcssComplete(nextDesc, abort);
                desc = nextDesc;
            } else {
                throw new VerifyException("Unhandled root " + next);
            }
        }
    }

    sealed interface Root permits INode, RDCSS_Descriptor {
        // Marker interface for classes which may appear as roots to a MutableTrieMap
    }

    @SuppressWarnings("checkstyle:typeName")
    private static final class RDCSS_Descriptor<K, V> implements Root {
        final INode<K, V> old;
        final MainNode<K, V> expectedmain;
        final INode<K, V> nv;

        // TODO: GH-60: can we use getAcquire()/setRelease() here?
        private volatile boolean committed;

        RDCSS_Descriptor(final INode<K, V> old, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
            this.old = old;
            this.expectedmain = expectedmain;
            this.nv = nv;
        }

        boolean readCommitted() {
            return committed;
        }

        void setCommitted() {
            committed = true;
        }
    }
}
