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

import static tech.pantheon.triemap.PresencePredicate.ABSENT;
import static tech.pantheon.triemap.PresencePredicate.PRESENT;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Similar to Scala&apos;s ListMap, this is a single-linked list of set of map entries. Aside from the java.util.Set
 * contract, this class fulfills the requirements for an immutable map entryset.
 *
 * @author Robert Varga
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
abstract sealed class LNodeEntries<K, V> extends LNodeEntry<K, V> {
    // Visible for testing
    static final class Single<K, V> extends LNodeEntries<K, V> {
        Single(final @NonNull K key, final @NonNull V value) {
            super(key, value);
        }

        @Override
        LNodeEntries<K, V> next() {
            return null;
        }
    }

    private static final class Multiple<K, V> extends LNodeEntries<K, V> {
        // Modified during remove only, otherwise final
        LNodeEntries<K, V> next;

        // Used in remove() only
        Multiple(final LNodeEntries<K, V> entry) {
            this(entry.key(), entry.value(), null);
        }

        Multiple(final @NonNull K key, final @NonNull V value, final LNodeEntries<K, V> next) {
            super(key, value);
            this.next = next;
        }

        @Override
        LNodeEntries<K, V> next() {
            return next;
        }
    }

    LNodeEntries(final @NonNull K key, final @NonNull V value) {
        super(key, value);
    }

    static <K,V> LNodeEntries<K, V> of(final @NonNull K k1, final @NonNull V v1,
            final @NonNull K k2, final @NonNull V v2) {
        return new Multiple<>(k1, v1, new Single<>(k2, v2));
    }

    /**
     * Return the remainder of this list. Useful for implementing Iterator-like contract. Null indicates there are no
     * more entries.
     *
     * @return Remainder of this list, or null if nothing remains
     */
    abstract LNodeEntries<K, V> next();

    final @Nullable V lookup(final @NonNull K key) {
        final var entry = findEntry(key);
        return entry != null ? entry.value() : null;
    }

    final boolean insert(final MutableTrieMap<K, V> ct, final INode<K, V> in, final LNode<K, V> ln,
            final @NonNull K key, final @NonNull V val) {
        final var entry = findEntry(key);
        return in.gcasWrite(ct, entry == null ? toInserted(ln, key, val) : toReplaced(ln, entry, val));
    }

    @Nullable Result<V> insertIf(final MutableTrieMap<K, V> ct, final INode<K, V> in, final LNode<K, V> ln,
            final @NonNull K key, final @NonNull V val, final Object cond) {
        final var entry = findEntry(key);
        if (entry == null) {
            return cond != null && cond != ABSENT || in.gcasWrite(ct, toInserted(ln, key, val)) ? Result.empty() : null;
        }
        if (cond == ABSENT) {
            return entry.toResult();
        } else if (cond == null || cond == PRESENT || cond.equals(entry.value())) {
            return in.gcasWrite(ct, toReplaced(ln, entry, val)) ? entry.toResult() : null;
        }
        return Result.empty();
    }

    @Nullable Result<V> remove(final MutableTrieMap<K, V> ct, final INode<K, V> in, final LNode<K, V> ln,
            final @NonNull K key, final @Nullable Object cond, final int hc) {
        final var entry = findEntry(key);
        if (entry == null) {
            // Key was not found, hence no modification is needed
            return Result.empty();
        }
        if (cond != null && !cond.equals(entry.value())) {
            // Value does not match
            return Result.empty();
        }

        // While remove() can return null, that case will never happen here, as we are starting off with two entries
        // so we cannot observe a null return here.
        final var map = VerifyException.throwIfNull(removeEntry(entry));

        // If the returned LNode would have only one element, we turn it intoa TNode, so it can be turned into SNode on
        // next lookup
        final var size = ln.size;
        final var next = size == 2 ? new TNode<>(ln, map.key(), map.value(), hc) : new LNode<>(ln, map, size - 1);

        return in.gcasWrite(ct, next) ? entry.toResult() : null;
    }

    private LNode<K, V> toInserted(final LNode<K, V> ln, final @NonNull K key, final @NonNull V val) {
        return new LNode<>(ln, insertEntry(key, val), ln.size + 1);
    }

    private LNode<K, V> toReplaced(final LNode<K, V> ln, final LNodeEntry<K, V> entry, final @NonNull V val) {
        return new LNode<>(ln, replace(entry, val), ln.size);
    }

    // Visible for testing
    final LNodeEntries<K, V> replace(final LNodeEntry<K, V> entry, final @NonNull V value) {
        final var removed = removeEntry(entry);
        return removed == null ? new Single<>(entry.key(), value) : new Multiple<>(entry.key(), value, removed);
    }

    // Visible for testing
    final @Nullable LNodeEntry<K, V> findEntry(final @NonNull K key) {
        // We do not perform recursion on purpose here, so we do not run out of stack if the key hashing fails.
        var entry = this;
        do {
            if (key.equals(entry.key())) {
                return entry;
            }

            entry = entry.next();
        } while (entry != null);

        return null;
    }

    // Visible for testing
    final LNodeEntries<K, V> insertEntry(final @NonNull K key, final @NonNull V value) {
        return new Multiple<>(key, value, this);
    }

    // Visible for testing
    final LNodeEntries<K, V> removeEntry(final LNodeEntry<K, V> entry) {
        if (entry == this) {
            return next();
        }

        // This will result in a list with a long tail, i.e last entry storing explicit null. Overhead is amortized
        // against the number of entries. We do not retain chains shorter than two, so the worst-case overhead is
        // half-a-reference for an entry.
        final var ret = new Multiple<>(this);

        var last = ret;
        var cur = next();
        while (cur != null) {
            // We cannot use equals() here, as it is wired to key equality and we must never compare entries based on
            // that property. This method is intended to remove a known reference, so identity is what we want.
            if (entry == cur) {
                last.next = cur.next();
                return ret;
            }

            final var tmp = new Multiple<>(cur);
            last.next = tmp;
            last = tmp;
            cur = cur.next();
        }

        throw new VerifyException("Failed to find entry " + entry);
    }
}
