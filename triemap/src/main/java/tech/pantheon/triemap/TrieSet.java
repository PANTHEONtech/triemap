/*
 * (C) Copyright 2019 PANTHEON.tech, s.r.o. and others.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An implementation of {@link Set} interface backed by a {@link TrieMap}. The implementation is fully concurrent
 * and additionally supports O(1) {@link MutableTrieSet mutable} and {@link ImmutableTrieSet immutable} isolated
 * snapshots.
 *
 * @param <E> the type of elements maintained by this set
 */
public abstract class TrieSet<E> implements Set<E>, Serializable {
    private static final long serialVersionUID = 0L;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Handled through writeReplace")
    private final transient TrieMap<E, Boolean> map;
    // Cached map keyset view, so we do not re-checking it all over
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Handled through writeReplace")
    private final transient AbstractKeySet<E> set;

    TrieSet(final TrieMap<E, Boolean> map) {
        this.map = requireNonNull(map);
        set = map.createKeySet();
    }

    public static <E> MutableTrieSet<E> create() {
        return new MutableTrieSet<>(TrieMap.create());
    }

    /**
     * Returns a snapshot of this TrieSet. This operation is lock-free and linearizable. Modification operations on this
     * Set and the returned one are isolated from each other.
     *
     * <p>
     * The snapshot is lazily updated - the first time some branch in the snapshot or this TrieSet are accessed, they
     * are rewritten. This means that the work of rebuilding both the snapshot and this TrieSet is distributed across
     * all the threads doing updates or accesses subsequent to the snapshot creation.
     *
     * @return A read-write TrieSet containing the contents of this set.
     */
    public final MutableTrieSet<E> mutableSnapshot() {
        return new MutableTrieSet<>(map.mutableSnapshot());
    }

    /**
     * Returns a read-only snapshot of this TrieSet. This operation is lock-free and linearizable.
     *
     * <p>
     * The snapshot is lazily updated - the first time some branch of this TrieSet are accessed, it is rewritten. The
     * work of creating the snapshot is thus distributed across subsequent updates and accesses on this TrieSet by all
     * threads. Note that the snapshot itself is never rewritten unlike when calling {@link #mutableSnapshot()}, but the
     * obtained snapshot cannot be modified.
     *
     * @return A read-only TrieSet containing the contents of this map.
     */
    public abstract ImmutableTrieSet<E> immutableSnapshot();


    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean remove(final Object o) {
        return map.remove(o) != null;
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean removeAll(final Collection<?> c) {
        return set.removeAll(c);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean add(final E e) {
        return map.putIfAbsent(e, Boolean.TRUE) == null;
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean addAll(final Collection<? extends E> c) {
        boolean ret = false;
        for (E e : c) {
            ret |= add(e);
        }
        return ret;
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean contains(final Object o) {
        return map.containsKey(o);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean containsAll(final Collection<?> c) {
        return set.containsAll(c);
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final boolean retainAll(final Collection<?> c) {
        return set.retainAll(c);
    }

    @Override
    public final boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final void clear() {
        map.clear();
    }

    @Override
    public final Object[] toArray() {
        return set.toArray();
    }

    @Override
    @SuppressWarnings("checkstyle:parameterName")
    public final <T> T[] toArray(final T[] a) {
        return set.toArray(a);
    }

    @Override
    public final void forEach(final Consumer<? super E> action) {
        set.forEach(action);
    }

    @Override
    public final boolean removeIf(final Predicate<? super E> filter) {
        return set.removeIf(filter);
    }

    @Override
    public final Iterator<E> iterator() {
        return set.iterator();
    }

    @Override
    public final Spliterator<E> spliterator() {
        return set.spliterator();
    }

    @Override
    public final Stream<E> stream() {
        return set.stream();
    }

    @Override
    public final Stream<E> parallelStream() {
        return set.parallelStream();
    }

    @Override
    public final int hashCode() {
        return set.hashCode();
    }

    @Override
    public final boolean equals(final Object obj) {
        return obj == this || set.equals(obj);
    }

    @Override
    public final String toString() {
        return set.toString();
    }

    final Object writeReplace() {
        return new SerializedForm(this);
    }

    final TrieMap<E, Boolean> map() {
        return map;
    }

    private static final class SerializedForm implements Externalizable {
        private static final long serialVersionUID = 0L;

        private transient TrieSet<?> set;

        @SuppressWarnings("checkstyle:redundantModifier")
        public SerializedForm() {
            // For Externalizable
        }

        SerializedForm(final TrieSet<?> set) {
            this.set = requireNonNull(set);
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            final var snap = set.immutableSnapshot();
            out.writeBoolean(set instanceof ImmutableTrieSet);
            out.writeInt(snap.size());
            for (Object e : snap) {
                out.writeObject(e);
            }
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            final boolean readOnly = in.readBoolean();
            final int size = in.readInt();
            if (size < 0) {
                throw new StreamCorruptedException("Expected non-negative size instead of " + size);
            }

            final var read = TrieSet.create();
            for (int i = 0; i < size; ++i) {
                read.add(in.readObject());
            }

            set = readOnly ? read.immutableSnapshot() : read;
        }

        Object readResolve() {
            return set;
        }
    }
}
