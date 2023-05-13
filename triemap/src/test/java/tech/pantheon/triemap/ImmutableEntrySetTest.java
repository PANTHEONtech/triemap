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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ImmutableEntrySetTest {
    private ImmutableEntrySet<Object, Object> set;

    @Before
    public void before() {
        set = TrieMap.create().immutableSnapshot().createEntrySet();
    }

    @Test
    public void testIsEmpty() {
        assertTrue(set.isEmpty());
    }

    @Test
    public void testSize() {
        assertEquals(0, set.size());
    }

    @Test
    public void testClear() {
        assertThrows(UnsupportedOperationException.class, () -> set.clear());
    }

    @Test
    public void testRemove() {
        assertThrows(UnsupportedOperationException.class, () -> set.remove(new Object()));
    }

    @Test
    public void testRemoveAll() {
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(List.of()));
    }

    @Test
    public void testRetainAll() {
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(List.of()));
    }

    @Test
    public void testIteratorSetValue() {
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
    public void testSpliteratorSetValue() {
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
