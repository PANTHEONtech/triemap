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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TestMapIterator {
    @Test
    void testMapIterator() {
        final Random random = new Random();

        for (int i = 0; i < 60 * 1000; i += 400 + random.nextInt(400)) {
            final var bt = TrieMap.<Integer, Integer>create();
            for (int j = 0; j < i; j++) {
                assertNull(bt.put(Integer.valueOf(j), Integer.valueOf(j)));
            }
            int count = 0;
            final var set = new HashSet<Integer>();
            for (var e : bt.entrySet()) {
                set.add(e.getKey());
                count++;
            }
            for (var j : set) {
                assertTrue(bt.containsKey(j));
            }
            for (var j : bt.keySet()) {
                assertTrue(set.contains(j));
            }

            assertEquals(i, count);
            assertEquals(i, bt.size());

            for (var e : bt.entrySet()) {
                assertSame(e.getValue(), bt.get(e.getKey()));
                e.setValue(e.getValue() + 1);
                assertEquals((Object)e.getValue(), e.getKey() + 1);
                assertEquals(e.getValue(), bt.get(e.getKey()));
                e.setValue(e.getValue() - 1);
            }

            final var it = bt.keySet().iterator();
            while (it.hasNext()) {
                final var k = it.next();
                assertTrue(bt.containsKey(k));
                it.remove();
                assertFalse(bt.containsKey(k));
            }

            assertEquals(0, bt.size());
            assertTrue(bt.isEmpty());
        }
    }

    @Test
    void testMapImmutableIterator() {
        final Random random = new Random();

        for (int i = 0; i < 60 * 1000; i += 400 + random.nextInt(400)) {
            final var bt = TrieMap.<Integer, Integer>create();
            for (int j = 0; j < i; j++) {
                assertNull(bt.put(Integer.valueOf(j), Integer.valueOf(j)));
            }
            int count = 0;
            final var set = new HashSet<Integer>();
            for (var e : bt.entrySet()) {
                set.add(e.getKey());
                count++;
            }
            for (var j : set) {
                assertTrue(bt.containsKey(j));
            }
            for (var j : bt.keySet()) {
                assertTrue(set.contains(j));
            }

            assertEquals(i, count);
            assertEquals(i, bt.size());
        }
    }

    @Test
    void testEmptyIterator() {
        failAdvance(TrieMap.create().iterator());
    }

    @Test
    void testEmptyReadOnlyIterator() {
        failAdvance(TrieMap.create().immutableIterator());
    }

    @Test
    void testEmptyReadOnlySnapshotIterator() {
        failAdvance(TrieMap.create().immutableSnapshot().iterator());
    }

    private static void failAdvance(final Iterator<?> it) {
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }
}
