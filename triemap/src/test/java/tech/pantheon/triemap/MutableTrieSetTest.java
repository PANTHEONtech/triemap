/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package tech.pantheon.triemap;

import static org.junit.Assert.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MutableTrieSetTest {
    @Test
    void createIsMutable() {
        assertInstanceOf(MutableTrieSet.class, TrieSet.create());
    }

    @Test
    void emptyIsEmpty() {
        assertEquals(0, TrieSet.create().size());
    }

    @Test
    void immutableSnapshotIsImmutable() {
        assertInstanceOf(ImmutableTrieSet.class, TrieSet.create().immutableSnapshot());
    }

    @Test
    void immutableSnapshotIsIsollated() {
        final var set = TrieSet.create();
        set.add(1);

        final var snapshot = set.immutableSnapshot();
        assertNotSame(set, snapshot);
        assertEquals(set, snapshot);

        set.add(2);
        assertEquals(1, snapshot.size());
        assertNotEquals(set, snapshot);
    }

    @Test
    void mutableSnapshotIsIsollated() {
        final var set = TrieSet.create();
        set.add(1);

        final var snapshot = set.mutableSnapshot();
        assertNotSame(set, snapshot);
        assertEquals(set, snapshot);

        snapshot.add(2);
        assertEquals(2, snapshot.size());
        assertNotEquals(set, snapshot);
    }

    @Test
    void addWorks() {
        final var set = TrieSet.create();
        assertEquals(Set.of(), set);
        assertTrue(set.add(1));
        assertEquals(Set.of(1), set);
        assertFalse(set.add(1));
        assertEquals(Set.of(1), set);
    }

    @Test
    void removeWorks() {
        final var set = TrieSet.create();
        assertEquals(Set.of(), set);
        set.add(1);
        assertEquals(Set.of(1), set);
        assertFalse(set.remove(2));
        assertEquals(Set.of(1), set);
        assertTrue(set.remove(1));
        assertEquals(Set.of(), set);
    }

    @Test
    void addAllWorks() {
        final var set = TrieSet.create();
        set.add(1);
        assertFalse(set.addAll(List.of(1)));

        assertTrue(set.addAll(List.of(2, 3)));
        assertEquals(Set.of(1,2, 3), set);
    }

    @Test
    void removeAllWorks() {
        final var set = TrieSet.create();
        set.add(1);
        assertFalse(set.removeAll(List.of(2)));
        assertTrue(set.removeAll(List.of(1, 2)));
        assertEquals(Set.of(), set);
    }

    @Test
    void addNullThrows() {
        final var set = TrieSet.create();
        assertThrows(NullPointerException.class, () -> set.add(null));
    }

    @Test
    void removeNullThrows() {
        final var set = TrieSet.create();
        assertThrows(NullPointerException.class, () -> set.remove(null));
    }

    @Test
    void equalsSelfIsTrue() {
        final var set = TrieSet.create();
        assertEquals(set, set);
    }

    @Test
    void equalsNullIsFalse() {
        // Scratching around our hear due to SonarCloud
        assertNotEquals(TrieSet.create(), null);
    }

    @Test
    void hashCodeEquality() {
        final var set = TrieSet.create();
        assertEquals(Set.of().hashCode(), set.hashCode());
        set.add(1);
        assertEquals(Set.of(1).hashCode(), set.hashCode());
        set.add(2);
        assertEquals(Set.of(1, 2).hashCode(), set.hashCode());
        set.add(3);
        assertEquals(Set.of(1, 2, 3).hashCode(), set.hashCode());
    }

    @Test
    void toStringEquality() {
        // Note: hashCode are stable and therefore iteration order should be stable
        final var set = TrieSet.create();
        assertEquals("[]", set.toString());
        set.add(1);
        assertEquals("[1]", set.toString());
        set.add(3);
        assertEquals("[1, 3]", set.toString());
        set.add(2);
        assertEquals("[1, 2, 3]", set.toString());
    }

    @Test
    void isEmptyWorks() {
        final var set = TrieSet.create();
        assertTrue(set.isEmpty());
        set.add(1);
        assertFalse(set.isEmpty());
        set.remove(1);
        assertTrue(set.isEmpty());
    }

    @Test
    void clearWorks() {
        final var set = TrieSet.create();
        set.add(1);
        set.clear();
        assertEquals(Set.of(), set);
    }

    @Test
    void toArrayWorks() {
        final var set = TrieSet.create();
        set.add(1);
        assertArrayEquals(new Object[] { 1 }, set.toArray());
        assertArrayEquals(new Integer[] { 1 }, set.toArray(new Integer[0]));
    }

    @Test
    void containsWorks() {
        final var set = TrieSet.create();
        assertFalse(set.contains(1));
        set.add(1);
        assertTrue(set.contains(1));
    }

    @Test
    void containsAllWorks() {
        final var set = TrieSet.create();
        assertFalse(set.containsAll(List.of(1)));
        set.add(1);
        assertTrue(set.containsAll(List.of(1)));
        assertFalse(set.containsAll(List.of(1, 2)));
        set.add(2);
        assertTrue(set.containsAll(List.of(1, 2)));
    }

    @Test
    void retainAllWorks() {
        final var set = TrieSet.create();
        set.add(1);
        assertEquals(Set.of(1), set);
        assertFalse(set.retainAll(List.of(1)));
        assertEquals(Set.of(1), set);
        assertTrue(set.retainAll(List.of(2)));
        assertEquals(Set.of(), set);
    }

    @Test
    void removeIfWorks() {
        final var set = TrieSet.create();
        set.add(1);

        assertFalse(set.removeIf(obj -> obj.equals(2)));
        assertEquals(Set.of(1), set);
        assertTrue(set.removeIf(obj -> obj.equals(1)));
        assertEquals(Set.of(), set);
    }

    @Test
    void emptySerialize() throws Exception {
        final var set = TrieSet.create();
        final var bytes = serialize(set);
        assertEquals(73, bytes.length);

        final var restored = assertInstanceOf(MutableTrieSet.class, deserialize(bytes));
        assertNotSame(set, restored);
        assertEquals(set, restored);
    }

    @Test
    void oneSerialize() throws Exception {
        final var set = TrieSet.create();
        set.add(1);
        final var bytes = serialize(set);
        assertEquals(150, bytes.length);

        final var restored = assertInstanceOf(MutableTrieSet.class, deserialize(bytes));
        assertNotSame(set, restored);
        assertEquals(set, restored);
    }

    @Test
    void corruptedSerialize() throws Exception {
        final var bytes = serialize(TrieSet.create());
        assertEquals(73, bytes.length);

        // size = -16777216
        bytes[68] = -1;
        final var cse = assertThrows(StreamCorruptedException.class, () -> deserialize(bytes));
        assertEquals("Expected non-negative size instead of -16777216", cse.getMessage());
    }

    private static byte[] serialize(final Serializable set) throws Exception {
        final var bos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(set);
        }
        return bos.toByteArray();
    }

    private static TrieSet<?> deserialize(final byte[] bytes) throws Exception {
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return assertInstanceOf(TrieSet.class, ois.readObject());
        }
    }
}
