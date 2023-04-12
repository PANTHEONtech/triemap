/*
 * (C) Copyright 2017 PANTHEON.tech, s.r.o. and others.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnapshotTest {
    private TrieMap<String, String> map;

    @BeforeEach
    void setUp() {
        map = TrieMap.create();
        map.put("k1", "v1");
        map.put("k2", "v2");
    }

    private static void assertMutableIsolation(final TrieMap<String, String> m1, final TrieMap<String, String> m2) {
        assertTrue(m2.containsKey("k1"));
        assertTrue(m2.containsKey("k2"));

        m1.remove("k1");
        assertFalse(m1.containsKey("k1"));
        assertTrue(m2.containsKey("k1"));

        m2.remove("k2");
        assertFalse(m1.containsKey("k1"));
        assertTrue(m2.containsKey("k1"));

        assertEquals(1, m1.size());
        assertEquals(1, m2.size());
    }

    @Test
    void testMutableSnapshotIsolation() {
        assertMutableIsolation(map, map.mutableSnapshot());
    }

    @Test
    void testMutableSnapshotIsolationAcrossImmutable() {
        final var snap = map.immutableSnapshot();
        assertTrue(snap.containsKey("k1"));
        assertTrue(snap.containsKey("k2"));

        assertMutableIsolation(map, snap.mutableSnapshot());

        assertTrue(snap.containsKey("k1"));
        assertTrue(snap.containsKey("k2"));

    }
}
