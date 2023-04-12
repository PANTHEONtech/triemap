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

import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MutableKeySetTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";

    private MutableKeySet<String> set;
    private MutableTrieMap<String, String> map;

    @BeforeEach
    void before() {
        map = TrieMap.create();
        map.put(KEY, VALUE);
        set = map.createKeySet();
    }

    @Test
    void testAdd() {
        assertThrows(UnsupportedOperationException.class, () -> set.add(null));
    }

    @Test
    void testAddAll() {
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(null));
    }

    @Test
    void testClear() {
        set.clear();
        assertTrue(map.isEmpty());
        assertTrue(set.isEmpty());
    }


    @Test
    void testContains() {
        assertTrue(set.contains(KEY));
        assertFalse(set.contains(VALUE));
    }

    @Test
    void testContainsNull() {
        assertThrows(NullPointerException.class, () -> set.contains(null));
    }

    @Test
    void testRemove() {
        assertFalse(set.remove(VALUE));
        assertEquals(1, map.size());
        assertEquals(1, set.size());
        assertTrue(set.remove(KEY));
        assertTrue(map.isEmpty());
    }

    @Test
    void testRemoveNull() {
        assertThrows(NullPointerException.class, () -> set.remove(null));
    }

    @Test
    void testIterator() {
        final Iterator<String> it = set.iterator();
        assertTrue(it.hasNext());
        assertEquals(KEY, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testImmutableIterator() {
        final Iterator<String> it = set.immutableIterator();
        assertTrue(it.hasNext());
        assertEquals(KEY, it.next());
        assertFalse(it.hasNext());
    }
}
