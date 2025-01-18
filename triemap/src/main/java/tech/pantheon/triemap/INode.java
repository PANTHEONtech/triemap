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

import static tech.pantheon.triemap.Constants.LEVEL_BITS;
import static tech.pantheon.triemap.LookupResult.RESTART;
import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.Nullable;

final class INode<K, V> implements Branch, MutableTrieMap.Root {
    private static final VarHandle MAINNODE;

    static {
        try {
            MAINNODE = MethodHandles.lookup().findVarHandle(INode.class, "mainnode", MainNode.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Gen gen;

    private volatile MainNode<K, V> mainnode;

    INode(final Gen gen, final MainNode<K, V> mainnode) {
        this.gen = gen;
        this.mainnode = mainnode;
    }

    MainNode<K, V> gcasRead(final TrieMap<?, ?> ct) {
        final var main = /* READ */ mainnode;
        final var prevval = /* READ */ main.readPrev();
        if (prevval == null) {
            return main;
        }

        return gcasComplete(main, ct);
    }

    private MainNode<K, V> gcasComplete(final MainNode<K, V> oldmain, final TrieMap<?, ?> ct) {
        var main = oldmain;
        while (main != null) {
            // complete the GCAS
            final var prev = /* READ */ main.readPrev();
            final var ctr = ct.readRoot(true);
            if (prev == null) {
                return main;
            }

            if (prev instanceof FailedNode) {
                // try to commit to previous value
                final var fn = (FailedNode<K, V>) prev;
                final var witness = (MainNode<K, V>) MAINNODE.compareAndExchange(this, main, fn.readPrev());
                if (witness == main) {
                    // TODO: second read of FailedNode.prev. Can a FailedNode move?
                    return fn.readPrev();
                }

                // Tail recursion: return GCAS_Complete(witness, ct);
                main = witness;
                continue;
            }

            // Assume that you've read the root from the generation G.
            // Assume that the snapshot algorithm is correct.
            // ==> you can only reach nodes in generations <= G.
            // ==> `gen` is <= G.
            // We know that `ctr.gen` is >= G.
            // ==> if `ctr.gen` = `gen` then they are both equal to G.
            // ==> otherwise, we know that either `ctr.gen` > G, `gen` < G,
            // or both
            if (ctr.gen == gen && !ct.isReadOnly()) {
                // try to commit
                if (main.casPrev(prev, null)) {
                    return main;
                }

                // Tail recursion: return GCAS_Complete(m, ct);
                continue;
            }

            // try to abort
            main.casPrev(prev, new FailedNode<>(prev));

            // Tail recursion: return GCAS_Complete(/* READ */ mainnode, ct);
            main = /* READ */ mainnode;
        }

        return null;
    }

    private boolean gcas(final MainNode<K, V> oldMain, final MainNode<K, V> newMain, final TrieMap<?, ?> ct) {
        newMain.writePrev(oldMain);
        if (MAINNODE.compareAndSet(this, oldMain, newMain)) {
            gcasComplete(newMain, ct);
            return /* READ */ newMain.readPrev() == null;
        }

        return false;
    }

    private INode<K, V> inode(final MainNode<K, V> cn) {
        return new INode<>(gen, cn);
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
                        if (gcas(cn, cn.renewed(startgen, ct), ct)) {
                            // Tail recursion: return rec_insert(k, v, hc, lev, parent, startgen, ct);
                            continue;
                        }

                        return false;
                    } else if (cnAtPos instanceof SNode) {
                        @SuppressWarnings("unchecked")
                        final var sn = (SNode<K, V>) cnAtPos;
                        if (sn.hc == hc && key.equals(sn.key)) {
                            return gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct);
                        }

                        final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                        final var nn = rn.updatedAt(pos, inode(CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, gen)),
                            gen);
                        return gcas(cn, nn, ct);
                    } else {
                        throw CNode.invalidElement(cnAtPos);
                    }
                }

                final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                final var ncnode = rn.insertedAt(pos, flag, new SNode<>(key, val, hc), gen);
                return gcas(cn, ncnode, ct);
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
        final var nn = rn.updatedAt(pos, inode(CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, gen)), gen);
        return gcas(cn, nn, ct) ? Result.empty() : null;
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

                if ((bmp & flag) != 0) {
                    // 1a) insert below
                    final var cnAtPos = cn.array[pos];
                    if (cnAtPos instanceof INode) {
                        @SuppressWarnings("unchecked")
                        final var in = (INode<K, V>) cnAtPos;
                        if (startgen == in.gen) {
                            return in.recInsertIf(key, val, hc, cond, lev + LEVEL_BITS, this, startgen, ct);
                        }

                        if (gcas(cn, cn.renewed(startgen, ct), ct)) {
                            // Tail recursion: return rec_insertif(k, v, hc, cond, lev, parent, startgen, ct);
                            continue;
                        }

                        return null;
                    } else if (cnAtPos instanceof SNode) {
                        @SuppressWarnings("unchecked")
                        final var sn = (SNode<K, V>) cnAtPos;
                        if (cond == null) {
                            if (sn.hc == hc && key.equals(sn.key)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return sn.toResult();
                                }

                                return null;
                            }

                            return insertDual(ct, cn, pos, sn, key, val, hc, lev);
                        } else if (cond == ABSENT) {
                            if (sn.hc == hc && key.equals(sn.key)) {
                                return sn.toResult();
                            }

                            return insertDual(ct, cn, pos, sn, key, val, hc, lev);
                        } else if (cond == PRESENT) {
                            if (sn.hc == hc && key.equals(sn.key)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return sn.toResult();
                                }
                                return null;
                            }

                            return Result.empty();
                        } else {
                            if (sn.hc == hc && key.equals(sn.key) && cond.equals(sn.value)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return sn.toResult();
                                }

                                return null;
                            }

                            return Result.empty();
                        }
                    } else {
                        throw CNode.invalidElement(cnAtPos);
                    }
                } else if (cond == null || cond == ABSENT) {
                    final var rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                    final var ncnode = rn.insertedAt(pos, flag, new SNode<>(key, val, hc), gen);
                    return gcas(cn, ncnode, ct) ? Result.empty() : null;
                } else {
                    return Result.empty();
                }
            } else if (m instanceof TNode) {
                clean(parent, ct, lev - LEVEL_BITS);
                return null;
            } else if (m instanceof LNode) {
                // 3) an l-node
                final var ln = (LNode<K, V>) m;
                final var entry = ln.get(key);

                if (cond == null) {
                    return entry != null ? replaceln(ln, entry, val, ct) ? entry.toResult() : null
                        : insertln(ln, key, val, ct) ? Result.empty() : null;
                } else if (cond == ABSENT) {
                    return entry != null ? entry.toResult() : insertln(ln, key, val, ct) ? Result.empty() : null;
                } else if (cond == PRESENT) {
                    return entry == null ? Result.empty() : replaceln(ln, entry, val, ct) ? entry.toResult() : null;
                } else {
                    return entry == null || !cond.equals(entry.getValue()) ? Result.empty()
                        : replaceln(ln, entry, val, ct) ? entry.toResult() : null;
                }
            } else {
                throw invalidElement(m);
            }
        }
    }

    private boolean insertln(final LNode<K, V> ln, final K key, final V val, final TrieMap<K, V> ct) {
        return gcas(ln, ln.insertChild(key, val), ct);
    }

    private boolean replaceln(final LNode<K, V> ln, final LNodeEntry<K, V> entry, final V val, final TrieMap<K, V> ct) {
        return gcas(ln, ln.replaceChild(entry, val), ct);
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

                    if (gcas(cn, cn.renewed(startgen, ct), ct)) {
                        // Tail recursion: return rec_lookup(k, hc, lev, parent, startgen, ct);
                        continue;
                    }

                    return RESTART;
                } else if (sub instanceof SNode) {
                    // 2) singleton node
                    @SuppressWarnings("unchecked")
                    final var sn = (SNode<K, V>) sub;
                    return sn.hc == hc && key.equals(sn.key) ? sn.value : null;
                } else {
                    throw CNode.invalidElement(sub);
                }
            } else if (m instanceof TNode) {
                // 3) non-live node
                return cleanReadOnly((TNode<K, V>) m, lev, parent, ct, key, hc);
            } else if (m instanceof LNode) {
                // 5) an l-node
                final var entry = ((LNode<K, V>) m).get(key);
                return entry != null ? entry.getValue() : null;
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
            } else if (gcas(cn, cn.renewed(startgen, ct), ct)) {
                res = recRemove(key, cond, hc, lev, parent, startgen, ct);
            } else {
                return null;
            }
        } else if (sub instanceof SNode) {
            @SuppressWarnings("unchecked")
            final var sn = (SNode<K, V>) sub;
            if (sn.hc != hc || !key.equals(sn.key) || cond != null && !cond.equals(sn.value)) {
                return Result.empty();
            }

            final var ncn = cn.removedAt(pos, flag, gen).toContracted(lev);
            if (gcas(cn, ncn, ct)) {
                res = sn.toResult();
            } else {
                return null;
            }
        } else {
            throw CNode.invalidElement(sub);
        }

        if (res != null && res.isPresent() && parent != null) {
            // never tomb at root
            final var n = gcasRead(ct);
            if (n instanceof TNode) {
                cleanParent(n, parent, ct, hc, lev, startgen);
            }
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

        if (cond != null && !cond.equals(entry.getValue())) {
            // Value does not match
            return Result.empty();
        }

        return gcas(ln, ln.removeChild(entry, hc), ct) ? entry.toResult() : null;
    }

    private void cleanParent(final Object nonlive, final INode<K, V> parent, final TrieMap<K, V> ct, final int hc,
            final int lev, final Gen startgen) {
        while (true) {
            final var pm = parent.gcasRead(ct);
            if (!(pm instanceof CNode)) {
                // parent is no longer a cnode, we're done
                return;
            }

            final var cn = (CNode<K, V>) pm;
            final int idx = hc >>> lev - LEVEL_BITS & 0x1f;
            final int bmp = cn.bitmap;
            final int flag = 1 << idx;
            if ((bmp & flag) == 0) {
                // somebody already removed this i-node, we're done
                return;
            }

            final int pos = Integer.bitCount(bmp & flag - 1);
            final var sub = cn.array[pos];
            if (sub == this && nonlive instanceof TNode<?, ?> tn) {
                final var ncn = cn.updatedAt(pos, tn.copyUntombed(), gen).toContracted(lev - LEVEL_BITS);
                if (!parent.gcas(cn, ncn, ct) && ct.readRoot().gen == startgen) {
                    // Tail recursion: cleanParent(nonlive, parent, ct, hc, lev, startgen);
                    continue;
                }
            }
            break;
        }
    }

    private void clean(final INode<K, V> nd, final TrieMap<K, V> ct, final int lev) {
        final var m = nd.gcasRead(ct);
        if (m instanceof CNode) {
            final var cn = (CNode<K, V>) m;
            nd.gcas(cn, cn.toCompressed(ct, lev, gen), ct);
        }
    }

    int size(final ImmutableTrieMap<?, ?> ct) {
        return VerifyException.throwIfNull(gcasRead(ct)).size(ct);
    }
}
