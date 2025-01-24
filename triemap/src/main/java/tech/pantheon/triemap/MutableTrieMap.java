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
import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

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

    private static final VarHandle VH;

    static {
        try {
            VH = MethodHandles.lookup().findVarHandle(MutableTrieMap.class, "root", Root.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Either an INode or a RDCSS_Descriptor
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Handled through writeReplace()")
    private transient volatile Root<K, V> root;

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
    INode<K, V> rdcssReadRoot(final boolean abort) {
        final var r = /* READ */ root;
        if (r instanceof INode<K, V> in) {
            return in;
        } else if (r instanceof RdcssDescriptor<K, V> desc) {
            return rdcssComplete(desc, abort);
        } else {
            throw new VerifyException("Unhandled root " + r);
        }
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
        if (!recInsert(readRoot(), key, value, hc)) {
            throw new VerifyException("Concurrent modification during serialization of map " + this);
        }
    }

    /**
     * Inserts a key value pair, overwriting the old pair if the keys match.
     *
     * @return true if successful, false otherwise
     */
    private boolean recInsert(final INode<K, V> first, final K key, final V val, final int hc) {
        final var startGen = first.gen;
        INode<K, V> parent = null;
        var current = first;
        int lev = 0;

        while (true) {
            final var m = current.gcasRead(this);

            if (m instanceof CNode<K, V> cn) {
                // 1) a multiway node
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) == 0) {
                    return cn.insert(this, current, pos, flag, key, val, hc);
                }

                // 1a) insert below
                final var cnAtPos = cn.array[pos];
                if (cnAtPos instanceof INode<K, V> in) {
                    // renew if needed
                    if (startGen != in.gen && !cn.renew(this, current, startGen)) {
                        return false;
                    }

                    // enter next level
                    parent = current;
                    current = in;
                    lev += LEVEL_BITS;
                    continue;
                } else if (cnAtPos instanceof SNode<K, V> sn) {
                    return cn.insert(this, current, pos, sn, key, val, hc, lev);
                } else {
                    throw invalidElement(cnAtPos);
                }
            } else if (m instanceof TNode) {
                clean(current, parent, lev);
                return false;
            } else if (m instanceof LNode<K, V> ln) {
                return ln.entries.insert(this, current, ln, key, val);
            } else {
                throw invalidElement(m);
            }
        }
    }

    private @NonNull Result<V> insertifhc(final K key, final int hc, final V value, final Object cond) {
        Result<V> res;
        do {
            // Keep looping as long as we do not get a reply
            res = recInsertIf(readRoot(), key, value, hc, cond);
        } while (res == null);

        return res;
    }

    /**
     * Inserts a new key value pair, given that a specific condition is met.
     *
     * @param cond
     *            null - don't care if the key was there
     *            KEY_ABSENT - key wasn't there
     *            KEY_PRESENT - key was there
     *            other value `val` - key must be bound to `val`
     * @return null if unsuccessful, Result(V) otherwise (indicating previous value bound to the key)
     */
    private @Nullable Result<V> recInsertIf(final INode<K, V> first, final K key, final V val, final int hc,
            final Object cond) {
        final var startgen = first.gen;
        INode<K, V> parent = null;
        var current = first;
        int lev = 0;

        while (true) {
            final var m = current.gcasRead(this);

            if (m instanceof CNode<K, V> cn) {
                // 1) a multiway node
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) == 0) {
                    // not found
                    return cond != null && cond != ABSENT || cn.insert(this, current, pos, flag, key, val, hc)
                        ? Result.empty() : null;
                }

                // 1a) insert below
                final var cnAtPos = cn.array[pos];
                if (cnAtPos instanceof INode<K, V> in) {
                    if (startgen != in.gen && !cn.renew(this, current, startgen)) {
                        return null;
                    }

                    // enter next level
                    parent = current;
                    current = in;
                    lev += LEVEL_BITS;
                } else if (cnAtPos instanceof SNode<K, V> sn) {
                    return cn.insertIf(this, current, pos, sn, key, val, hc, cond, lev);
                } else {
                    throw invalidElement(cnAtPos);
                }
            } else if (m instanceof TNode) {
                clean(current, parent, lev);
                return null;
            } else if (m instanceof LNode<K, V> ln) {
                // 3) an l-node
                return ln.entries.insertIf(this, current, ln, key, val, cond);
            } else {
                throw invalidElement(m);
            }
        }
    }

    private @NonNull Result<V> removehc(final K key, final Object cond, final int hc) {
        Result<V> res;
        do {
            // Keep looping as long as we do not get a reply
            final var r = readRoot();
            res = r.remove(this, r.gen, hc, key, cond, 0, null);
        } while (res == null);

        return res;
    }

    private Root<K, V> casRoot(final Root<K, V> prev, final Root<K, V> next) {
        return (Root<K, V>) VH.compareAndExchange(this, prev, next);
    }

    private boolean rdcssRoot(final INode<K, V> ov, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
        final var desc = new RdcssDescriptor<>(ov, expectedmain, nv);
        final var witness = casRoot(ov, desc);
        if (witness == ov) {
            rdcssComplete(desc, false);
            return desc.readCommitted();
        }

        return false;
    }

    private INode<K, V> rdcssComplete(final RdcssDescriptor<K, V> initial, final boolean abort) {
        var desc = initial;

        while (true) {
            final Root<K, V> next;

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

            if (next instanceof INode<K, V> inode) {
                return inode;
            } else if (next instanceof RdcssDescriptor<K, V> nextDesc) {
                // Tail recursion: return rdcssComplete(nextDesc, abort);
                desc = nextDesc;
            } else {
                throw new VerifyException("Unhandled root " + next);
            }
        }
    }

    sealed interface Root<K, V> permits INode, RdcssDescriptor {
        // Marker interface for classes which may appear as roots to a MutableTrieMap
    }

    private static final class RdcssDescriptor<K, V> implements Root<K, V> {
        final INode<K, V> old;
        final MainNode<K, V> expectedmain;
        final INode<K, V> nv;

        // TODO: GH-60: can we use getAcquire()/setRelease() here?
        private volatile boolean committed;

        RdcssDescriptor(final INode<K, V> old, final MainNode<K, V> expectedmain, final INode<K, V> nv) {
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
