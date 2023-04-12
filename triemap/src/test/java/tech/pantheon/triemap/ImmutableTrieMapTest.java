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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImmutableTrieMapTest {
    private ImmutableTrieMap<Object, Object> map;

    @BeforeEach
    void before() {
        map = ImmutableTrieMap.create().immutableSnapshot();
    }

    @Test
    void testClear() {
        assertThrows(UnsupportedOperationException.class, () -> map.clear());
    }

    @Test
    void testCompute() {
        assertThrows(UnsupportedOperationException.class, () -> map.compute(null, null));
    }

    @Test
    void testComputeIfAbsent() {
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(null, null));
    }

    @Test
    void testComputeIfPresent() {
        assertThrows(UnsupportedOperationException.class, () -> map.computeIfPresent(null, null));
    }

    @Test
    void testMerge() {
        assertThrows(UnsupportedOperationException.class, () -> map.merge(null, null, null));
    }

    @Test
    void testPut() {
        assertThrows(UnsupportedOperationException.class, () -> map.put(null, null));
    }

    @Test
    void testPutAll() {
        assertThrows(UnsupportedOperationException.class, () -> map.putAll(null));
    }

    @Test
    void testPutIfAbsent() {
        assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(null, null));
    }

    @Test
    void testRemove() {
        assertThrows(UnsupportedOperationException.class, () -> map.remove(null));
    }

    @Test
    void testRemoveExact() {
        assertThrows(UnsupportedOperationException.class, () -> map.remove(null, null));
    }

    @Test
    void testReplace() {
        assertThrows(UnsupportedOperationException.class, () -> map.replace(null, null));
    }

    @Test
    void testReplaceExact() {
        assertThrows(UnsupportedOperationException.class, () -> map.replace(null, null, null));
    }
}
