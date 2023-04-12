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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test that read-only iterators do not allow for any updates.
 * Test that non read-only iterators allow for updates.
 */
class TestReadOnlyAndUpdatableIterators {
    private static final int MAP_SIZE = 200;

    private TrieMap<Integer, Integer> bt;

    @BeforeEach
    void setUp() {
        bt = TrieMap.create();
        for (int j = 0; j < MAP_SIZE; j++) {
            assertNull(bt.put(Integer.valueOf(j), Integer.valueOf(j)));
        }
    }

    private static void trySet(final Iterator<Entry<Integer, Integer>> it) {
        final Entry<Integer, Integer> entry = it.next();
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(0));
    }

    private static void tryRemove(final Iterator<?> it) {
        it.next();
        assertThrows(UnsupportedOperationException.class, () -> it.remove());
    }

    @Test
    void testReadOnlyIteratorSet() {
        trySet(bt.immutableIterator());
    }

    @Test
    void testReadOnlyIteratorRemove() {
        tryRemove(bt.immutableIterator());
    }

    @Test
    void testReadOnlySnapshotReadOnlyIteratorSet() {
        trySet(bt.immutableSnapshot().immutableIterator());
    }

    @Test
    void testReadOnlySnapshotReadOnlyIteratorRemove() {
        tryRemove(bt.immutableSnapshot().immutableIterator());
    }

    @Test
    void testReadOnlySnapshotIteratorSet() {
        trySet(bt.immutableSnapshot().iterator());
    }

    @Test
    void testReadOnlySnapshotIteratorRemove() {
        tryRemove(bt.immutableSnapshot().iterator());
    }

    @Test
    void testIterator() {
        Iterator<Entry<Integer, Integer>> it = bt.iterator();
        it.next().setValue(0);
        it.remove();

        // All changes are done on the original map
        assertEquals(MAP_SIZE - 1, bt.size());
    }

    @Test
    void testSnapshotIterator() {
        TrieMap<Integer, Integer> snapshot = bt.mutableSnapshot();
        Iterator<Entry<Integer, Integer>> it = snapshot.iterator();
        it.next().setValue(0);
        it.remove();

        // All changes are done on the snapshot, not on the original map
        // Map size should remain unchanged
        assertEquals(MAP_SIZE, bt.size());
        // snapshot size was changed
        assertEquals(MAP_SIZE - 1, snapshot.size());
    }
}
