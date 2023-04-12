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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MutableEntrySetTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";

    private MutableEntrySet<String, String> set;
    private MutableTrieMap<String, String> map;

    @BeforeEach
    void before() {
        map = TrieMap.create();
        map.put(KEY, VALUE);
        set = map.createEntrySet();
    }

    @Test
    void testAdd() {
        assertThrows(UnsupportedOperationException.class, () -> set.add(null));
    }

    @Test
    void testClear() {
        set.clear();
        assertTrue(map.isEmpty());
        assertTrue(set.isEmpty());
    }

    @Test
    void testContains() {
        assertFalse(set.contains(null));
        assertFalse(set.contains(new SimpleImmutableEntry<>(null, VALUE)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(KEY, null)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(KEY, KEY)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(VALUE, KEY)));
        assertTrue(set.contains(new SimpleImmutableEntry<>(KEY, VALUE)));
    }

    @Test
    void testRemove() {
        assertFalse(set.remove(null));
        assertEquals(1, map.size());
        assertEquals(1, set.size());
        assertFalse(set.remove(new SimpleImmutableEntry<>(null, VALUE)));
        assertEquals(1, map.size());
        assertEquals(1, set.size());
        assertFalse(set.remove(new SimpleImmutableEntry<>(KEY, null)));
        assertEquals(1, map.size());
        assertEquals(1, set.size());
        assertFalse(set.remove(new SimpleImmutableEntry<>(KEY, KEY)));
        assertEquals(1, map.size());
        assertEquals(1, set.size());
        assertTrue(set.remove(new SimpleImmutableEntry<>(KEY, VALUE)));
        assertTrue(map.isEmpty());
    }

    @Test
    void testIterator() {
        final Iterator<Entry<String, String>> it = set.iterator();
        assertTrue(it.hasNext());
        assertEquals(new SimpleImmutableEntry<>(KEY, VALUE), it.next());
        assertFalse(it.hasNext());
    }
}
