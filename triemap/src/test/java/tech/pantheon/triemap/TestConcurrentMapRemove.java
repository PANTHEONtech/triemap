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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestConcurrentMapRemove {
    private static final int COUNT = 50 * 1000;

    @Test
    void testConcurrentMapRemove() {
        final var map = TrieMap.<Integer, Object>create();

        for (int i = 128; i < COUNT; i++) {
            assertFalse(map.remove(i, i));
            assertNull(map.put(i, i));
            assertFalse(map.remove(i, "lol"));
            assertTrue(map.containsKey(i));
            assertTrue(map.remove(i, i));
            assertFalse(map.containsKey(i));
            assertNull(map.put(i, i));
        }
    }

    @Test
    void testConflictingHash() {
        final var k1 = new ZeroHashInt(1);
        final var k2 = new ZeroHashInt(2);
        final var k3 = new ZeroHashInt(3);
        final var k3dup = new ZeroHashInt(3);
        final var v1 = new ZeroHashInt(4);
        final var v2 = new ZeroHashInt(5);
        final var v3 = new ZeroHashInt(6);
        final var v3dup = new ZeroHashInt(6);

        final var map = TrieMap.<ZeroHashInt, ZeroHashInt>create();
        // Pre-populate an LNode
        assertNull(map.putIfAbsent(k1, v1));
        assertNull(map.putIfAbsent(k2, v2));
        assertNull(map.putIfAbsent(k3, v3));

        assertFalse(map.remove(k3, v2));
        assertTrue(map.remove(k3dup, v3dup));
    }
}
