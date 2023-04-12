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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestDelete {
    @Test
    void testNullSimple() {
        var tm = TrieMap.create();
        assertThrows(NullPointerException.class, () -> tm.remove(null));
    }

    @Test
    void testNullKey() {
        var tm = TrieMap.create();
        assertThrows(NullPointerException.class, () -> tm.remove(null, ""));
    }

    @Test
    void testNullValue() {
        var tm = TrieMap.create();
        assertThrows(NullPointerException.class, () -> tm.remove("", null));
    }

    @Test
    void testNullBoth() {
        var tm = TrieMap.create();
        assertThrows(NullPointerException.class, () -> tm.remove(null, null));
    }

    @Test
    void testClear() {
        final var bt = TrieMap.<Integer, Integer>create();
        bt.put(1, 1);
        bt.clear();
        assertTrue(bt.isEmpty());
        assertEquals(0, bt.size());
    }

    @Test
    void testDelete() {
        final var bt = TrieMap.<Integer, Integer>create();

        for (int i = 0; i < 10000; i++) {
            assertNull(bt.put(Integer.valueOf(i), Integer.valueOf(i)));
            assertEquals(Integer.valueOf(i), bt.get(Integer.valueOf(i)));
        }

        checkAddInsert(bt, 536);
        checkAddInsert(bt, 4341);
        checkAddInsert(bt, 8437);

        for (int i = 0; i < 10000; i++) {
            assertNotNull(bt.remove(Integer.valueOf(i)));
            assertNull(bt.get(Integer.valueOf(i)));
        }

        bt.toString();
    }

    /**
     * Test if the Map.remove(Object, Object) method works correctly for hash collisions, which are handled by LNode.
     */
    @Test
    void testRemoveObjectLNode() {
        final var bt = TrieMap.<ZeroHashInt, ZeroHashInt>create();

        for (int i = 0; i < 100; i++) {
            final var v = new ZeroHashInt(i);
            assertNull(bt.put(v, v));
        }

        for (int i = 0; i < 100; i++) {
            final var v = new ZeroHashInt(i);
            assertTrue(bt.remove(v, v));
        }
    }

    private static void checkAddInsert(final TrieMap<Integer, Integer> bt, final int key) {
        final Integer v = key;
        bt.remove(v);
        Integer foundV = bt.get(v);
        assertNull(foundV);
        assertNull(bt.put(v, v));
        foundV = bt.get(v);
        assertEquals(v, foundV);

        assertEquals(v, bt.put(v, Integer.valueOf(-1)));
        assertEquals(Integer.valueOf(-1), bt.put(v, v));
    }
}
