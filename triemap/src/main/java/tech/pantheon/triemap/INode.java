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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

final class INode<K, V> implements Branch<K, V>, MutableTrieMap.Root<K, V> {
    /**
     * A GCAS-protected {@link MainNode}. This can be effectively either a {@link FailedGcas} or a {@link MainNode}.
     */
    private sealed interface Gcas<K, V> permits FailedGcas, TryGcas {
        // Nothing else
    }

    /**
     * A {@link MainNode} that needs to be restored into the stream.
     */
    // Visible for testing
    @NonNullByDefault
    record FailedGcas<K, V>(MainNode<K, V> orig) implements Gcas<K, V> {
        FailedGcas {
            requireNonNull(orig);
        }

        @Override
        public String toString() {
            return "FailedNode(" + orig + ")";
        }
    }

    /**
     * A {@link MainNode} has potentially some restoration work attached to it. The work is tracked in {@code prev}
     * field and undergoes different lifecycles based on which constructor is invoked.
     *
     * <p>When we start with a non-null {@link CorLNode}, we are the next step in committing that node. That settles
     * in {@code gcasComplete()} to either {@code null} on success or to {@link FailedGcas} on failure.
     *
     * <p>Once we have observed a {@code null}, we switch to tracking read-side consistency and {@code prev} can get
     * intermittently to clean up work, which will go away once last referent is gone.
     */
    abstract static sealed class TryGcas<K, V> implements Gcas<K, V> permits MainNode {
        @SuppressFBWarnings(value = "UUF_UNUSED_FIELD",
            justification = "https://github.com/spotbugs/spotbugs/issues/2749")
        // Never accessed directly, always go through PREV_VH
        private volatile Gcas<K, V> prev;

        private TryGcas(final Gcas<K, V> prev) {
            // plain store lurking in shadows cast by final fields in subclasses
            PREV_VH.set(this, prev);
        }

        /**
         * Constructor for GCAS states which are considered committed.
         */
        TryGcas() {
            this((Gcas<K, V>) null);
        }

        /**
         * Constructor for GCAS states which follow an {@link CNode} previous state.
         */
        TryGcas(final CNode<K, V> prev) {
            this((Gcas<K, V>) requireNonNull(prev));
        }

        /**
         * Constructor for GCAS states which follow an {@link LNode} previous state.
         */
        TryGcas(final LNode<K, V> prev) {
            this((Gcas<K, V>) requireNonNull(prev));
        }
    }

    // VarHandle for operations on "mainNode" field
    private static final VarHandle MAIN_VH;
    // VarHandle for operations on 'TryGcas.prev' field
    private static final VarHandle PREV_VH;

    static {
        final var lookup = MethodHandles.lookup();
        try {
            MAIN_VH = lookup.findVarHandle(INode.class, "main", MainNode.class);
            PREV_VH = MethodHandles.lookup().findVarHandle(TryGcas.class, "prev", Gcas.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final Gen gen;

    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    // Never accessed directly, always go through MAIN_VH
    private volatile MainNode<K, V> main;

    INode(final Gen gen, final MainNode<K, V> mainNode) {
        MAIN_VH.set(this, mainNode);
        this.gen = gen;
    }

    INode(final INode<K, V> prev, final SNode<K, V> sn, final K key, final V val, final int hc, final int lev) {
        this(prev.gen, CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, prev.gen));
    }

    @NonNull MainNode<K, V> gcasReadNonNull(final TrieMap<?, ?> ct) {
        return VerifyException.throwIfNull(gcasRead(ct));
    }

    MainNode<K, V> gcasRead(final TrieMap<?, ?> ct) {
        return gcasComplete(ct, (MainNode<K, V>) MAIN_VH.getVolatile(this));
    }

    boolean gcasWrite(final TrieMap<?, ?> ct, final MainNode<K, V> next) {
        // note: plain read of 'next', i.e. take a look at what we are about to CAS-out
        if (MAIN_VH.compareAndSet(this, VerifyException.throwIfNull(PREV_VH.get(next)), next)) {
            // established as write, now try to complete it and report if we have succeeded
            gcasComplete(ct, next);
            return PREV_VH.getVolatile(next) == null;
        }
        return false;
    }

    private MainNode<K, V> gcasComplete(final TrieMap<?, ?> ct, final MainNode<K, V> firstMain) {
        // complete the GCAS starting at firstMain
        var currentMain = firstMain;

        while (true) {
            var prev = (Gcas<K, V>) PREV_VH.getVolatile(currentMain);
            if (prev == null) {
                // Validated, we are done
                return currentMain;
            }

            final MainNode<K, V> nextMain;
            while (true) {
                if (prev instanceof FailedGcas<K, V> prevFailed) {
                    // pick up insert failure where the other case left off:
                    final var orig = prevFailed.orig;
                    // try to commit to previous value
                    final var witness = (MainNode<K, V>) MAIN_VH.compareAndExchange(this, currentMain, orig);
                    if (witness == currentMain) {
                        // successful: counts as a valid read
                        return orig;
                    }

                    // Tail recursion: gcasComplete(witness, ct);
                    nextMain = witness;
                    break;
                } else if (prev instanceof MainNode<K, V> prevMain) {
                    // Assume that you've read the root from the generation G.
                    // Assume that the snapshot algorithm is correct.
                    // ==> you can only reach nodes in generations <= G.
                    // ==> `gen` is <= G.
                    // We know that `ctr.gen` is >= G.
                    // ==> if `ctr.gen` = `gen` then they are both equal to G.
                    // ==> otherwise, we know that either `ctr.gen` > G, `gen` < G,
                    // or both
                    //
                    // Note: we deal with the abort case first
                    final var ctr = ct.readRoot(true);
                    if (ctr.gen != gen || ct.isReadOnly()) {
                        // try to abort
                        PREV_VH.compareAndSet(currentMain, prev, new FailedGcas<>(prevMain));
                        // Tail recursion: gcasComplete(mainNode, ct)
                        nextMain = (MainNode<K, V>) MAIN_VH.getVolatile(this);
                        break;
                    }

                    // try to commit
                    final var witness = (Gcas<K, V>) PREV_VH.compareAndExchange(currentMain, prev, null);
                    if (witness == prev || witness == null) {
                        // successful commit: it was either us or someone racing with us
                        return currentMain;
                    }

                    // internal recursion: same main, different prev
                    prev = witness;
                } else {
                    throw new VerifyException("Unexpected gcas " + prev);
                }
            }

            if (nextMain == null) {
                // TODO: when can this happen?
                return null;
            }

            // Tail recursion: gcasComplete(nextMain, ct)
            currentMain = nextMain;
        }
    }

    INode<K, V> copyToGen(final TrieMap<K, V> ct, final Gen ngen) {
        return new INode<>(ngen, gcasRead(ct));
    }

    int readSize(final ImmutableTrieMap<?, ?> ct) {
        return gcasReadNonNull(ct).size(ct);
    }

    /**
     * Looks up the value associated with the key.
     *
     * @param ct the ctrie
     * @param hc the hash code
     * @param key the key
     * @return null if no value has been found, RESTART if the operation was not successful, or any other value
     *         otherwise
     */
    Object lookup(final TrieMap<K, V> ct, final Gen startGen, final int hc, final K key, final int lev,
            final INode<K, V> parent) {
        final var m = gcasRead(ct);

        if (m instanceof CNode<K, V> cn) {
            return cn.lookup(ct, startGen, hc, key, lev, this);
        } else if (m instanceof TNode<K, V> tn) {
            // 3) non-live node
            if (ct.isReadOnly()) {
                // read-only side does not clean up
                return tn.hc == hc && key.equals(tn.key) ? tn.value : null;
            }
            // read-write: perform some clean up and restart
            clean(ct, parent, lev);
            return TrieMap.RESTART;
        } else if (m instanceof LNode<K, V> ln) {
            // 5) an l-node
            return ln.entries.lookup(key);
        } else {
            throw invalidElement(m);
        }
    }

    /**
     * Inserts a key value pair, overwriting the old pair if the keys match.
     *
     * @return true if successful, false otherwise
     */
    boolean insert(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final K key, final V val,
            final int lev, final INode<K, V> parent) {
        final var m = gcasRead(ct);
        if (m instanceof CNode<K, V> cn) {
            return cn.insert(ct, startGen, hc, key, val, lev, this);
        } else if (m instanceof TNode) {
            clean(ct, parent, lev);
            return false;
        } else if (m instanceof LNode<K, V> ln) {
            return ln.entries.insert(ct, this, ln, key, val);
        } else {
            throw invalidElement(m);
        }
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
    @Nullable Result<V> insertIf(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final K key,
            final V val, final Object cond, final int lev, final INode<K, V> parent) {
        final var m = gcasRead(ct);
        if (m instanceof CNode<K, V> cn) {
            return cn.insertIf(ct, startGen, hc, key, val, cond, lev, this);
        } else if (m instanceof TNode) {
            clean(ct, parent, lev);
            return null;
        } else if (m instanceof LNode<K, V> ln) {
            // 3) an l-node
            return ln.entries.insertIf(ct, this, ln, key, val, cond);
        } else {
            throw invalidElement(m);
        }
    }

    /**
     * Removes the key associated with the given value.
     *
     * @param cond
     *            if null, will remove the key regardless of the value;
     *            otherwise removes only if binding contains that exact key
     *            and value
     * @return null if not successful, an Result indicating the previous
     *         value otherwise
     */
    @Nullable Result<V> remove(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final K key,
            final Object cond, final int lev, final INode<K, V> parent) {
        final var m = gcasRead(ct);
        if (m instanceof CNode<K, V> cn) {
            final var res = cn.remove(ct, startGen, hc, key, cond, lev, this);
            // never tomb at root
            if (res != null && res.isPresent() && parent != null && parent.gcasRead(ct) instanceof TNode<K, V> tn) {
                cleanParent(ct, startGen, hc, tn, parent, lev);
            }
            return res;
        } else if (m instanceof TNode) {
            clean(ct, parent, lev);
            return null;
        } else if (m instanceof LNode<K, V> ln) {
            return ln.entries.remove(ct, this, ln, key, cond, hc);
        } else {
            throw invalidElement(m);
        }
    }

    private void clean(final TrieMap<K, V> ct, final INode<K, V> parent, final int lev) {
        if (parent.gcasRead(ct) instanceof CNode<K, V> cn) {
            parent.gcasWrite(ct, cn.toCompressed(ct, gen, lev - LEVEL_BITS));
        }
    }

    // gcasRead() and if it is a TNode convert it to an SNode instead. Called indirectly via cn.toCompressed() from
    // clean() just above
    Branch<K, V> resurrect(final TrieMap<K, V> ct) {
        return gcasReadNonNull(ct) instanceof TNode<K, V> tn ? new SNode<>(tn) : this;
    }

    private void cleanParent(final MutableTrieMap<K, V> ct, final Gen startGen, final int hc, final TNode<K, V> tn,
            final INode<K, V> parent, final int lev) {
        while (true) {
            if (!(parent.gcasRead(ct) instanceof CNode<K, V> cn)) {
                // parent is no longer a cnode, we're done
                return;
            }

            final int idx = hc >>> lev - LEVEL_BITS & 0x1f;
            final int bmp = cn.bitmap;
            final int flag = 1 << idx;
            if ((bmp & flag) == 0) {
                // somebody already removed this i-node, we're done
                return;
            }

            final int pos = Integer.bitCount(bmp & flag - 1);
            final var sub = cn.array[pos];
            if (sub != this) {
                // some other range is occupying our slot, we're done
                return;
            }

            // retry while we make progress and the tree is does not move to next generation
            if (parent.gcasWrite(ct, cn.toContracted(gen, pos, tn, lev - LEVEL_BITS))
                || ct.readRoot().gen != startGen) {
                return;
            }
        }
    }

    // Visible for testing
    static VerifyException invalidElement(final MainNode<?, ?> elem) {
        throw new VerifyException("An INode can host only a CNode, a TNode or an LNode, not " + elem);
    }
}
