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
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class TestConcurrentMapComputeIfPresent {
    private static final int COUNT = 50 * 1000;

    @Test
    void testConcurrentMapComputeIfPresentDoesNotComputeIfAbsent() {
        final var map = TrieMap.create();

        for (int i = 0; i < COUNT; i++) {
            assertNull(map.computeIfPresent(i, (k, v) -> fail("Should not have called function")));
            assertFalse(map.containsKey(i));
        }
    }


    @Test
    void testConcurrentMapComputeIfPresent() {
        final var map = TrieMap.create();

        for (int i = 0; i < COUNT; i++) {
            map.put(i, "42");
            assertEquals(i + " -> 42", map.computeIfPresent(i, (k, v) -> k + " -> " + v));
            assertEquals(i + " -> 42", map.get(i));
        }
    }

    @Test
    void testConcurrentMapComputeIfPresentRemovesValueIfComputesNull() {
        final var map = TrieMap.create();

        for (int i = 0; i < COUNT; i++) {
            map.put(i, "42");
            assertNull(map.computeIfPresent(i, (k, v) -> null));
            assertFalse(map.containsKey(i));
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

        final var map = TrieMap.<ZeroHashInt, ZeroHashInt>create();
        // Pre-populate an LNode
        assertNull(map.putIfAbsent(k1, v1));
        assertNull(map.putIfAbsent(k2, v2));
        assertNull(map.putIfAbsent(k3, v3));

        // Check with identical key
        assertSame(v3, map.computeIfPresent(k3, (k, v) -> v3));
        // Check with equivalent key
        assertSame(v3, map.computeIfPresent(k3dup, (k, v) -> v3));
    }
}
