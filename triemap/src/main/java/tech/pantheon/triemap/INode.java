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
import static tech.pantheon.triemap.LookupResult.RESTART;
import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

final class INode<K, V> implements Branch, MutableTrieMap.Root {
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

    private final Gen gen;

    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    // Never accessed directly, always go through MAIN_VH
    private volatile MainNode<K, V> main;

    INode(final Gen gen, final MainNode<K, V> mainNode) {
        MAIN_VH.set(this, mainNode);
        this.gen = gen;
    }

    @NonNull MainNode<K, V> gcasReadNonNull(final TrieMap<?, ?> ct) {
        return VerifyException.throwIfNull(gcasRead(ct));
    }

    MainNode<K, V> gcasRead(final TrieMap<?, ?> ct) {
        return gcasComplete((MainNode<K, V>) MAIN_VH.getVolatile(this), ct);
    }

    private boolean gcasWrite(final MainNode<K, V> next, final TrieMap<?, ?> ct) {
        // note: plain read of 'next', i.e. take a look at what we are about to CAS-out
        if (MAIN_VH.compareAndSet(this, VerifyException.throwIfNull(PREV_VH.get(next)), next)) {
            // established as write, now try to complete it and report if we have succeeded
            gcasComplete(next, ct);
            return PREV_VH.getVolatile(next) == null;
        }
        return false;
    }

    private MainNode<K, V> gcasComplete(final MainNode<K, V> firstMain, final TrieMap<?, ?> ct) {
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

    private INode<K, V> inode(final SNode<K, V> sn, final K key, final V val, final int hc, final int lev) {
        return new INode<>(gen, CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, gen));
    }

    INode<K, V> copyToGen(final Gen ngen, final TrieMap<?, ?> ct) {
        return new INode<>(ngen, gcasRead(ct));
    }

    /**
     * Inserts a key value pair, overwriting the old pair if the keys match.
     *
     * @return true if successful, false otherwise
     */
    boolean recInsert(final K key, final V value, final int hc, final int lev, final INode<K, V> parent,
            final TrieMap<K, V> ct) {
        return recInsert(key, value, hc, lev, parent, gen, ct);
    }

    private boolean recInsert(final K key, final V val, final int hc, final int lev, final INode<K, V> parent,
            final Gen startgen, final TrieMap<K, V> ct) {
        while (true) {
            final MainNode<K, V> m = gcasRead(ct);

            if (m instanceof CNode) {
                // 1) a multiway node
                final var cn = (CNode<K, V>) m;
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) != 0) {
                    // 1a) insert below
                    final var cnAtPos = cn.array[pos];
                    if (cnAtPos instanceof INode) {
                        @SuppressWarnings("unchecked")
                        final var in = (INode<K, V>) cnAtPos;
                        if (startgen == in.gen) {
                            return in.recInsert(key, val, hc, lev + LEVEL_BITS, this, startgen, ct);
                        }
                        if (gcasWrite(cn.renewed(startgen, ct), ct)) {
                            // Tail recursion: return rec_insert(k, v, hc, lev, parent, startgen, ct);
                            continue;
                        }

                        return false;
                    } else if (cnAtPos instanceof SNode) {
                        @SuppressWarnings("unchecked")
                        final var sn = (SNode<K, V>) cnAtPos;
                        if (sn.matches(hc, key)) {
                            return gcasWrite(cn.updatedAt(pos, key, val, hc, gen), ct);
                        }

                        final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                        return gcasWrite(rn.updatedAt(pos, inode(sn, key, val, hc, lev), gen), ct);
                    } else {
                        throw CNode.invalidElement(cnAtPos);
                    }
                }

                final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                return gcasWrite(rn.toInsertedAt(cn, gen, pos, flag, key, val, hc), ct);
            } else if (m instanceof TNode) {
                clean(parent, ct, lev - LEVEL_BITS);
                return false;
            } else if (m instanceof LNode) {
                final var ln = (LNode<K, V>) m;
                final var entry = ln.get(key);
                return entry != null ? replaceln(ln, entry, val, ct) : insertln(ln, key, val, ct);
            } else {
                throw invalidElement(m);
            }
        }
    }

    static VerifyException invalidElement(final MainNode<?, ?> elem) {
        throw new VerifyException("An INode can host only a CNode, a TNode or an LNode, not " + elem);
    }

    private @Nullable Result<V> insertDual(final TrieMap<K, V> ct, final CNode<K, V> cn, final int pos,
            final SNode<K, V> sn, final K key, final V val, final int hc, final int lev) {
        final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
        return gcasWrite(rn.toUpdatedAt(cn, pos, inode(sn, key, val, hc, lev), gen), ct) ? Result.empty() : null;
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
    @Nullable Result<V> recInsertIf(final K key, final V val, final int hc, final Object cond, final int lev,
            final INode<K, V> parent, final TrieMap<K, V> ct) {
        return recInsertIf(key, val, hc, cond, lev, parent, gen, ct);
    }

    private @Nullable Result<V> recInsertIf(final K key, final V val, final int hc, final Object cond, final int lev,
            final INode<K, V> parent, final Gen startgen, final TrieMap<K, V> ct) {
        while (true) {
            final var m = gcasRead(ct);

            if (m instanceof CNode) {
                // 1) a multiway node
                final var cn = (CNode<K, V>) m;
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) == 0) {
                    // not found
                    if (cond == null || cond == ABSENT) {
                        final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                        if (!gcasWrite(rn.toInsertedAt(cn, gen, pos, flag, key, val, hc), ct)) {
                            return null;
                        }
                    }
                    return Result.empty();
                }

                // 1a) insert below
                final var cnAtPos = cn.array[pos];
                if (cnAtPos instanceof SNode<?, ?> sn) {
                    return insertIf(cn, pos, sn, key, val, hc, cond, lev, ct);
                }
                if (!(cnAtPos instanceof INode<?, ?> inode)) {
                    throw CNode.invalidElement(cnAtPos);
                }
                @SuppressWarnings("unchecked")
                final var in = (INode<K, V>) inode;
                if (startgen == in.gen) {
                    return in.recInsertIf(key, val, hc, cond, lev + LEVEL_BITS, this, startgen, ct);
                }
                if (!gcasWrite(cn.renewed(startgen, ct), ct)) {
                    return null;
                }

                // Tail recursion: return rec_insertif(k, v, hc, cond, lev, parent, startgen, ct);
            } else if (m instanceof TNode) {
                clean(parent, ct, lev - LEVEL_BITS);
                return null;
            } else if (m instanceof LNode<?, ?> ln) {
                // 3) an l-node
                return insertIf(ln, key, val, cond, ct);
            } else {
                throw invalidElement(m);
            }
        }
    }

    private @Nullable Result<V> insertIf(final CNode<K, V> cn, final int pos, final SNode<?, ?> snode, final K key,
            final V val, final int hc, final Object cond, final int lev, final TrieMap<K, V> ct) {
        @SuppressWarnings("unchecked")
        final var sn = (SNode<K, V>) snode;
        if (!sn.matches(hc, key)) {
            return cond == null || cond == ABSENT ? insertDual(ct, cn, pos, sn, key, val, hc, lev) : Result.empty();
        }
        if (cond == ABSENT) {
            return sn.toResult();
        } else if (cond == null || cond == PRESENT || cond.equals(sn.value())) {
            return gcasWrite(cn.updatedAt(pos, key, val, hc, gen), ct) ? sn.toResult() : null;
        }
        return Result.empty();
    }

    private @Nullable Result<V> insertIf(final LNode<?, ?> lnode, final K key, final V val, final Object cond,
            final TrieMap<K, V> ct) {
        @SuppressWarnings("unchecked")
        final var ln = (LNode<K, V>) lnode;
        final var entry = ln.get(key);

        if (entry == null) {
            return cond != null && cond != ABSENT || insertln(ln, key, val, ct) ? Result.empty() : null;
        }
        if (cond == ABSENT) {
            return entry.toResult();
        } else if (cond == null || cond == PRESENT || cond.equals(entry.value())) {
            return replaceln(ln, entry, val, ct) ? entry.toResult() : null;
        }
        return Result.empty();
    }

    private boolean insertln(final LNode<K, V> ln, final K key, final V val, final TrieMap<K, V> ct) {
        return gcasWrite(ln.insertChild(key, val), ct);
    }

    private boolean replaceln(final LNode<K, V> ln, final LNodeEntry<K, V> entry, final V val, final TrieMap<K, V> ct) {
        return gcasWrite(ln.replaceChild(entry, val), ct);
    }

    /**
     * Looks up the value associated with the key.
     *
     * @return null if no value has been found, RESTART if the operation
     *         wasn't successful, or any other value otherwise
     */
    Object recLookup(final K key, final int hc, final int lev, final INode<K, V> parent, final TrieMap<K, V> ct) {
        return recLookup(key, hc, lev, parent, gen, ct);
    }

    private Object recLookup(final K key, final int hc, final int lev, final INode<K, V> parent, final Gen startgen,
            final TrieMap<K, V> ct) {
        while (true) {
            final var m = gcasRead(ct);

            if (m instanceof CNode) {
                // 1) a multinode
                final var cn = (CNode<K, V>) m;
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
                if (sub instanceof INode) {
                    @SuppressWarnings("unchecked")
                    final var in = (INode<K, V>) sub;
                    if (ct.isReadOnly() || startgen == in.gen) {
                        return in.recLookup(key, hc, lev + LEVEL_BITS, this, startgen, ct);
                    }

                    if (gcasWrite(cn.renewed(startgen, ct), ct)) {
                        // Tail recursion: return rec_lookup(k, hc, lev, parent, startgen, ct);
                        continue;
                    }

                    return RESTART;
                } else if (sub instanceof SNode) {
                    // 2) singleton node
                    @SuppressWarnings("unchecked")
                    final var sn = (SNode<K, V>) sub;
                    return sn.matches(hc, key) ? sn.value() : null;
                } else {
                    throw CNode.invalidElement(sub);
                }
            } else if (m instanceof TNode) {
                // 3) non-live node
                return cleanReadOnly((TNode<K, V>) m, lev, parent, ct, key, hc);
            } else if (m instanceof LNode) {
                // 5) an l-node
                final var entry = ((LNode<K, V>) m).get(key);
                return entry != null ? entry.value() : null;
            } else {
                throw invalidElement(m);
            }
        }
    }

    private Object cleanReadOnly(final TNode<K, V> tn, final int lev, final INode<K, V> parent,
            final TrieMap<K, V> ct, final K key, final int hc) {
        if (ct.isReadOnly()) {
            return tn.hc == hc && key.equals(tn.key) ? tn.value : null;
        }

        clean(parent, ct, lev - LEVEL_BITS);
        return RESTART;
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
    @Nullable Result<V> recRemove(final K key, final Object cond, final int hc, final int lev, final INode<K, V> parent,
            final TrieMap<K, V> ct) {
        return recRemove(key, cond, hc, lev, parent, gen, ct);
    }

    private @Nullable Result<V> recRemove(final K key, final Object cond, final int hc, final int lev,
            final INode<K, V> parent, final Gen startgen, final TrieMap<K, V> ct) {
        final var m = gcasRead(ct);

        if (m instanceof CNode) {
            return recRemove((CNode<K, V>) m, key, cond, hc, lev, parent, startgen, ct);
        } else if (m instanceof TNode) {
            clean(parent, ct, lev - LEVEL_BITS);
            return null;
        } else if (m instanceof LNode) {
            return recRemove((LNode<K, V>) m, key, cond, hc, ct);
        } else {
            throw invalidElement(m);
        }
    }

    private @Nullable Result<V> recRemove(final CNode<K, V> cn, final K key, final Object cond, final int hc,
            final int lev, final INode<K, V> parent, final Gen startgen, final TrieMap<K, V> ct) {
        final int idx = hc >>> lev & 0x1f;
        final int bmp = cn.bitmap;
        final int flag = 1 << idx;
        if ((bmp & flag) == 0) {
            return Result.empty();
        }

        final int pos = Integer.bitCount(bmp & flag - 1);
        final var sub = cn.array[pos];
        final Result<V> res;
        if (sub instanceof INode) {
            @SuppressWarnings("unchecked")
            final var in = (INode<K, V>) sub;
            if (startgen == in.gen) {
                res = in.recRemove(key, cond, hc, lev + LEVEL_BITS, this, startgen, ct);
            } else if (gcasWrite(cn.renewed(startgen, ct), ct)) {
                res = recRemove(key, cond, hc, lev, parent, startgen, ct);
            } else {
                return null;
            }
        } else if (sub instanceof SNode) {
            @SuppressWarnings("unchecked")
            final var sn = (SNode<K, V>) sub;
            if (sn.matches(hc, key) && (cond == null || cond.equals(sn.value()))) {
                if (gcasWrite(cn.removedAt(pos, flag, gen).toContracted(cn, lev), ct)) {
                    res = sn.toResult();
                } else {
                    return null;
                }
            } else {
                return Result.empty();
            }
        } else {
            throw CNode.invalidElement(sub);
        }

        // never tomb at root
        if (res != null && res.isPresent() && parent != null && gcasRead(ct) instanceof TNode<?, ?> tnode) {
            cleanParent(tnode, parent, ct, hc, lev, startgen);
        }

        return res;
    }

    private @Nullable Result<V> recRemove(final LNode<K, V> ln, final K key, final Object cond, final int hc,
            final TrieMap<K, V> ct) {
        final var entry = ln.get(key);
        if (entry == null) {
            // Key was not found, hence no modification is needed
            return Result.empty();
        }

        if (cond != null && !cond.equals(entry.value())) {
            // Value does not match
            return Result.empty();
        }

        return gcasWrite(ln.removeChild(entry, hc), ct) ? entry.toResult() : null;
    }

    private void cleanParent(final TNode<?, ?> tn, final INode<K, V> parent, final TrieMap<K, V> ct, final int hc,
            final int lev, final Gen startgen) {
        while (true) {
            if (!(parent.gcasRead(ct) instanceof CNode<?, ?> cnode)) {
                // parent is no longer a cnode, we're done
                return;
            }

            @SuppressWarnings("unchecked")
            final var cn = (CNode<K, V>) cnode;
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

            if (parent.gcasWrite(cn.updatedAt(pos, tn.copyUntombed(), gen).toContracted(cn, lev - LEVEL_BITS), ct)
                || ct.readRoot().gen != startgen) {
                // (mumble-mumble) and we're done
                return;
            }
            // Tail recursion: cleanParent(tn, parent, ct, hc, lev, startgen);
        }
    }

    private void clean(final INode<K, V> nd, final TrieMap<K, V> ct, final int lev) {
        if (nd.gcasRead(ct) instanceof CNode<?, ?> cnode) {
            @SuppressWarnings("unchecked")
            final var cn = (CNode<K, V>) cnode;
            nd.gcasWrite(cn.toCompressed(ct, lev, gen), ct);
        }
    }

    int size(final ImmutableTrieMap<?, ?> ct) {
        return gcasReadNonNull(ct).size(ct);
    }
}
