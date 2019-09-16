/*
 * (C) Copyright 2016 Pantheon Technologies, s.r.o. and others.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class INode<K, V> extends BasicNode {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<INode, MainNode> MAINNODE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(INode.class, MainNode.class, "mainnode");

    private final Gen gen;

    private volatile MainNode<K, V> mainnode;

    INode(final Gen gen, final MainNode<K, V> mainnode) {
        this.gen = gen;
        this.mainnode = mainnode;
    }

    MainNode<K, V> gcasRead(final TrieMap<?, ?> ct) {
        MainNode<K, V> main = /* READ */ mainnode;
        MainNode<K, V> prevval = /* READ */ main.readPrev();
        if (prevval == null) {
            return main;
        }

        return gcasComplete(main, ct);
    }

    private MainNode<K, V> gcasComplete(final MainNode<K, V> oldmain, final TrieMap<?, ?> ct) {
        MainNode<K, V> main = oldmain;
        while (main != null) {
            // complete the GCAS
            final MainNode<K, V> prev = /* READ */ main.readPrev();
            final INode<?, ?> ctr = ct.readRoot(true);
            if (prev == null) {
                return main;
            }

            if (prev instanceof FailedNode) {
                // try to commit to previous value
                FailedNode<K, V> fn = (FailedNode<K, V>) prev;
                if (MAINNODE_UPDATER.compareAndSet(this, main, fn.readPrev())) {
                    return fn.readPrev();
                }

                // Tail recursion: return GCAS_Complete(/* READ */ mainnode, ct);
                main = /* READ */ mainnode;
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
        if (MAINNODE_UPDATER.compareAndSet(this, oldMain, newMain)) {
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
                final CNode<K, V> cn = (CNode<K, V>) m;
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) != 0) {
                    // 1a) insert below
                    final BasicNode cnAtPos = cn.array[pos];
                    if (cnAtPos instanceof INode) {
                        @SuppressWarnings("unchecked")
                        final INode<K, V> in = (INode<K, V>) cnAtPos;
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
                        final SNode<K, V> sn = (SNode<K, V>) cnAtPos;
                        if (sn.hc == hc && ct.equal(sn.key, key)) {
                            return gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct);
                        }

                        final CNode<K, V> rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                        final MainNode<K, V> nn = rn.updatedAt(pos, inode(
                            CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, gen)), gen);
                        return gcas(cn, nn, ct);
                    } else {
                        throw CNode.invalidElement(cnAtPos);
                    }
                }

                final CNode<K, V> rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                final MainNode<K, V> ncnode = rn.insertedAt(pos, flag, new SNode<>(key, val, hc), gen);
                return gcas(cn, ncnode, ct);
            } else if (m instanceof TNode) {
                clean(parent, ct, lev - LEVEL_BITS);
                return false;
            } else if (m instanceof LNode) {
                final LNode<K, V> ln = (LNode<K, V>) m;
                final LNodeEntry<K, V> entry = ln.get(ct.equiv(), key);
                return entry != null ? replaceln(ln, entry, val, ct) : insertln(ln, key, val, ct);
            } else {
                throw invalidElement(m);
            }
        }
    }

    static VerifyException invalidElement(final BasicNode elem) {
        throw new VerifyException("An INode can host only a CNode, a TNode or an LNode, not " + elem);
    }

    @SuppressFBWarnings(value = "NP_OPTIONAL_RETURN_NULL",
            justification = "Returning null Optional indicates the need to restart.")
    private Optional<V> insertDual(final TrieMap<K, V> ct, final CNode<K, V> cn, final int pos, final SNode<K, V> sn,
            final K key, final V val, final int hc, final int lev) {
        final CNode<K, V> rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
        final MainNode<K, V> nn = rn.updatedAt(pos, inode(CNode.dual(sn, key, val, hc, lev + LEVEL_BITS, gen)), gen);
        return gcas(cn, nn, ct) ? Optional.empty() : null;
    }

    /**
     * Inserts a new key value pair, given that a specific condition is met.
     *
     * @param cond
     *            null - don't care if the key was there
     *            KEY_ABSENT - key wasn't there
     *            KEY_PRESENT - key was there
     *            other value `val` - key must be bound to `val`
     * @return null if unsuccessful, Optional(V) otherwise (indicating previous value bound to the key)
     */
    Optional<V> recInsertIf(final K key, final V val, final int hc, final Object cond, final int lev,
            final INode<K, V> parent, final TrieMap<K, V> ct) {
        return recInsertIf(key, val, hc, cond, lev, parent, gen, ct);
    }

    @SuppressFBWarnings(value = "NP_OPTIONAL_RETURN_NULL",
            justification = "Returning null Optional indicates the need to restart.")
    private Optional<V> recInsertIf(final K key, final V val, final int hc, final Object cond, final int lev,
            final INode<K, V> parent, final Gen startgen, final TrieMap<K, V> ct) {
        while (true) {
            final MainNode<K, V> m = gcasRead(ct);

            if (m instanceof CNode) {
                // 1) a multiway node
                final CNode<K, V> cn = (CNode<K, V>) m;
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;
                final int mask = flag - 1;
                final int pos = Integer.bitCount(bmp & mask);

                if ((bmp & flag) != 0) {
                    // 1a) insert below
                    final BasicNode cnAtPos = cn.array[pos];
                    if (cnAtPos instanceof INode) {
                        @SuppressWarnings("unchecked")
                        final INode<K, V> in = (INode<K, V>) cnAtPos;
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
                        final SNode<K, V> sn = (SNode<K, V>) cnAtPos;
                        if (cond == null) {
                            if (sn.hc == hc && ct.equal(sn.key, key)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return Optional.of(sn.value);
                                }

                                return null;
                            }

                            return insertDual(ct, cn, pos, sn, key, val, hc, lev);
                        } else if (cond == ABSENT) {
                            if (sn.hc == hc && ct.equal(sn.key, key)) {
                                return Optional.of(sn.value);
                            }

                            return insertDual(ct, cn, pos, sn, key, val, hc, lev);
                        } else if (cond == PRESENT) {
                            if (sn.hc == hc && ct.equal(sn.key, key)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return Optional.of(sn.value);
                                }
                                return null;
                            }

                            return Optional.empty();
                        } else {
                            if (sn.hc == hc && ct.equal(sn.key, key) && cond.equals(sn.value)) {
                                if (gcas(cn, cn.updatedAt(pos, new SNode<>(key, val, hc), gen), ct)) {
                                    return Optional.of(sn.value);
                                }

                                return null;
                            }

                            return Optional.empty();
                        }
                    } else {
                        throw CNode.invalidElement(cnAtPos);
                    }
                } else if (cond == null || cond == ABSENT) {
                    final CNode<K, V> rn = cn.gen == gen ? cn : cn.renewed(gen, ct);
                    final CNode<K, V> ncnode = rn.insertedAt(pos, flag, new SNode<>(key, val, hc), gen);
                    if (gcas(cn, ncnode, ct)) {
                        return Optional.empty();
                    }

                    return null;
                } else {
                    return Optional.empty();
                }
            } else if (m instanceof TNode) {
                clean(parent, ct, lev - LEVEL_BITS);
                return null;
            } else if (m instanceof LNode) {
                // 3) an l-node
                final LNode<K, V> ln = (LNode<K, V>) m;
                final LNodeEntry<K, V> entry = ln.get(ct.equiv(), key);

                if (cond == null) {
                    if (entry != null) {
                        return replaceln(ln, entry, val, ct) ? Optional.of(entry.getValue()) : null;
                    }

                    return insertln(ln, key, val, ct) ? Optional.empty() : null;
                } else if (cond == ABSENT) {
                    if (entry != null) {
                        return Optional.of(entry.getValue());
                    }

                    return insertln(ln, key, val, ct) ? Optional.empty() : null;
                } else if (cond == PRESENT) {
                    if (entry == null) {
                        return Optional.empty();
                    }

                    return replaceln(ln, entry, val, ct) ? Optional.of(entry.getValue()) : null;
                } else {
                    if (entry == null || !cond.equals(entry.getValue())) {
                        return Optional.empty();
                    }

                    return replaceln(ln, entry, val, ct) ? Optional.of(entry.getValue()) : null;
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
            final MainNode<K, V> m = gcasRead(ct);

            if (m instanceof CNode) {
                // 1) a multinode
                final CNode<K, V> cn = (CNode<K, V>) m;
                final int idx = hc >>> lev & 0x1f;
                final int flag = 1 << idx;
                final int bmp = cn.bitmap;

                if ((bmp & flag) == 0) {
                    // 1a) bitmap shows no binding
                    return null;
                }

                // 1b) bitmap contains a value - descend
                final int pos = bmp == 0xffffffff ? idx : Integer.bitCount(bmp & flag - 1);
                final BasicNode sub = cn.array[pos];
                if (sub instanceof INode) {
                    @SuppressWarnings("unchecked")
                    final INode<K, V> in = (INode<K, V>) sub;
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
                    final SNode<K, V> sn = (SNode<K, V>) sub;
                    if (sn.hc == hc && ct.equal(sn.key, key)) {
                        return sn.value;
                    }

                    return null;
                } else {
                    throw CNode.invalidElement(sub);
                }
            } else if (m instanceof TNode) {
                // 3) non-live node
                return cleanReadOnly((TNode<K, V>) m, lev, parent, ct, key, hc);
            } else if (m instanceof LNode) {
                // 5) an l-node
                final LNodeEntry<K, V> entry = ((LNode<K, V>) m).get(ct.equiv(), key);
                return entry != null ? entry.getValue() : null;
            } else {
                throw invalidElement(m);
            }
        }
    }

    private Object cleanReadOnly(final TNode<K, V> tn, final int lev, final INode<K, V> parent,
            final TrieMap<K, V> ct, final K key, final int hc) {
        if (ct.isReadOnly()) {
            if (tn.hc == hc && ct.equal(tn.key, key)) {
                return tn.value;
            }

            return null;
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
     * @return null if not successful, an Optional indicating the previous
     *         value otherwise
     */
    Optional<V> recRemove(final K key, final Object cond, final int hc, final int lev, final INode<K, V> parent,
            final TrieMap<K, V> ct) {
        return recRemove(key, cond, hc, lev, parent, gen, ct);
    }

    @SuppressFBWarnings(value = "NP_OPTIONAL_RETURN_NULL",
            justification = "Returning null Optional indicates the need to restart.")
    private Optional<V> recRemove(final K key, final Object cond, final int hc, final int lev, final INode<K, V> parent,
            final Gen startgen, final TrieMap<K, V> ct) {
        final MainNode<K, V> m = gcasRead(ct);

        if (m instanceof CNode) {
            final CNode<K, V> cn = (CNode<K, V>) m;
            final int idx = hc >>> lev & 0x1f;
            final int bmp = cn.bitmap;
            final int flag = 1 << idx;
            if ((bmp & flag) == 0) {
                return Optional.empty();
            }

            final int pos = Integer.bitCount(bmp & flag - 1);
            final BasicNode sub = cn.array[pos];
            final Optional<V> res;
            if (sub instanceof INode) {
                @SuppressWarnings("unchecked")
                final INode<K, V> in = (INode<K, V>) sub;
                if (startgen == in.gen) {
                    res = in.recRemove(key, cond, hc, lev + LEVEL_BITS, this, startgen, ct);
                } else {
                    if (gcas(cn, cn.renewed(startgen, ct), ct)) {
                        res = recRemove(key, cond, hc, lev, parent, startgen, ct);
                    } else {
                        res = null;
                    }
                }
            } else if (sub instanceof SNode) {
                @SuppressWarnings("unchecked")
                final SNode<K, V> sn = (SNode<K, V>) sub;
                if (sn.hc == hc && ct.equal(sn.key, key) && (cond == null || cond.equals(sn.value))) {
                    final MainNode<K, V> ncn = cn.removedAt(pos, flag, gen).toContracted(lev);
                    if (gcas(cn, ncn, ct)) {
                        res = Optional.of(sn.value);
                    } else {
                        res = null;
                    }
                } else {
                    res = Optional.empty();
                }
            } else {
                throw CNode.invalidElement(sub);
            }

            if (res == null || !res.isPresent()) {
                return res;
            }

            if (parent != null) {
                // never tomb at root
                final MainNode<K, V> n = gcasRead(ct);
                if (n instanceof TNode) {
                    cleanParent(n, parent, ct, hc, lev, startgen);
                }
            }

            return res;
        } else if (m instanceof TNode) {
            clean(parent, ct, lev - LEVEL_BITS);
            return null;
        } else if (m instanceof LNode) {
            final LNode<K, V> ln = (LNode<K, V>) m;
            final LNodeEntry<K, V> entry = ln.get(ct.equiv(), key);
            if (entry == null) {
                // Key was not found, hence no modification is needed
                return Optional.empty();
            }

            final V value = entry.getValue();
            if (cond != null && !cond.equals(value)) {
                // Value does not match
                return Optional.empty();
            }

            return gcas(ln, ln.removeChild(entry, hc), ct) ? Optional.of(value) : null;
        } else {
            throw invalidElement(m);
        }
    }

    private void cleanParent(final Object nonlive, final INode<K, V> parent, final TrieMap<K, V> ct, final int hc,
            final int lev, final Gen startgen) {
        while (true) {
            final MainNode<K, V> pm = parent.gcasRead(ct);
            if (!(pm instanceof CNode)) {
                // parent is no longer a cnode, we're done
                return;
            }

            final CNode<K, V> cn = (CNode<K, V>) pm;
            final int idx = hc >>> lev - LEVEL_BITS & 0x1f;
            final int bmp = cn.bitmap;
            final int flag = 1 << idx;
            if ((bmp & flag) == 0) {
                // somebody already removed this i-node, we're done
                return;
            }

            final int pos = Integer.bitCount(bmp & flag - 1);
            final BasicNode sub = cn.array[pos];
            if (sub == this && nonlive instanceof TNode) {
                final TNode<?, ?> tn = (TNode<?, ?>) nonlive;
                final MainNode<K, V> ncn = cn.updatedAt(pos, tn.copyUntombed(), gen).toContracted(lev - LEVEL_BITS);
                if (!parent.gcas(cn, ncn, ct)) {
                    if (ct.readRoot().gen == startgen) {
                        // Tail recursion: cleanParent(nonlive, parent, ct, hc, lev, startgen);
                        continue;
                    }
                }
            }
            break;
        }
    }

    private void clean(final INode<K, V> nd, final TrieMap<K, V> ct, final int lev) {
        final MainNode<K, V> m = nd.gcasRead(ct);
        if (m instanceof CNode) {
            final CNode<K, V> cn = (CNode<K, V>) m;
            nd.gcas(cn, cn.toCompressed(ct, lev, gen), ct);
        }
    }

    int size(final ImmutableTrieMap<?, ?> ct) {
        return gcasRead(ct).size(ct);
    }
}
