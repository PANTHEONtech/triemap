/*
 * (C) Copyright 2018 PANTHEON.tech, s.r.o. and others.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImmutableEntrySetTest {
    private ImmutableEntrySet<Object, Object> set;

    @BeforeEach
    void before() {
        set = TrieMap.create().immutableSnapshot().createEntrySet();
    }

    @Test
    void testIsEmpty() {
        assertTrue(set.isEmpty());
    }

    @Test
    void testSize() {
        assertEquals(0, set.size());
    }

    @Test
    void testClear() {
        assertThrows(UnsupportedOperationException.class, () -> set.clear());
    }

    @Test
    void testRemove() {
        final var arg = new Object();
        assertThrows(UnsupportedOperationException.class, () -> set.remove(arg));
    }

    @Test
    void testRemoveAll() {
        final var arg = List.of();
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(arg));
    }

    @Test
    void testRemoveIf() {
        final Predicate<Object> arg = obj -> false;
        assertThrows(UnsupportedOperationException.class, () -> set.removeIf(arg));
    }

    @Test
    void testRetainAll() {
        final var arg = List.of();
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(arg));
    }

    @Test
    void testIteratorSetValue() {
        final var map = TrieMap.create();
        map.put("a", "b");
        assertEquals(Map.of("a", "b"), map);

        final var snap = map.immutableSnapshot();
        assertEquals(Map.of("a", "b"), snap);

        final var it = snap.createEntrySet().iterator();
        assertTrue(it.hasNext());

        final var entry = it.next();
        assertEquals(Map.entry("a", "b"), entry);
        assertFalse(it.hasNext());

        assertThrows(UnsupportedOperationException.class, () -> entry.setValue("c"));
        assertEquals(Map.entry("a", "b"), entry);
        assertEquals(Map.of("a", "b"), snap);
    }

    @Test
    void testSpliteratorSetValue() {
        final var map = TrieMap.create();
        map.put("a", "b");
        assertEquals(Map.of("a", "b"), map);

        final var snap = map.immutableSnapshot();
        final var sp = snap.createEntrySet().spliterator();
        assertTrue(sp.tryAdvance(entry -> {
            assertThrows(UnsupportedOperationException.class, () -> entry.setValue("c"));
        }));
        assertEquals(Map.of("a", "b"), snap);
    }
}
