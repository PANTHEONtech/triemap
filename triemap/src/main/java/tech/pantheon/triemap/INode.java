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
        return gcasComplete((MainNode<K, V>) MAIN_VH.getVolatile(this), ct);
    }

    boolean gcasWrite(final MainNode<K, V> next, final TrieMap<?, ?> ct) {
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

    INode<K, V> copyToGen(final Gen ngen, final TrieMap<K, V> ct) {
        return new INode<>(ngen, gcasRead(ct));
    }

    int size(final ImmutableTrieMap<?, ?> ct) {
        return gcasReadNonNull(ct).size(ct);
    }
}
