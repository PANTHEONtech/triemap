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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class MutableEntrySetTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String VALUE2 = "value2";

    private MutableEntrySet<String, String> set;
    private MutableTrieMap<String, String> map;

    @Before
    public void before() {
        map = TrieMap.create();
        map.put(KEY, VALUE);
        set = map.createEntrySet();
    }

    @Test
    public void testAdd() {
        assertThrows(UnsupportedOperationException.class, () -> set.add(null));
    }

    @Test
    public void testClear() {
        set.clear();
        assertTrue(map.isEmpty());
        assertTrue(set.isEmpty());
    }

    @Test
    public void testContains() {
        assertFalse(set.contains(null));
        assertFalse(set.contains(new SimpleImmutableEntry<>(null, VALUE)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(KEY, null)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(KEY, KEY)));
        assertFalse(set.contains(new SimpleImmutableEntry<>(VALUE, KEY)));
        assertTrue(set.contains(new SimpleImmutableEntry<>(KEY, VALUE)));
    }

    @Test
    public void testRemove() {
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
    public void testIterator() {
        final var it = set.iterator();
        assertTrue(it.hasNext());
        assertEquals(new SimpleImmutableEntry<>(KEY, VALUE), it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorSetValue() {
        final var it = set.iterator();
        assertTrue(it.hasNext());

        final var entry = it.next();
        assertEquals(Map.entry(KEY, VALUE), entry);
        assertFalse(it.hasNext());

        entry.setValue(VALUE2);
        assertEquals(Map.entry(KEY, VALUE2), entry);
        assertEquals(Map.of(KEY, VALUE2), map);
    }

    @Test
    public void testSpliteratorSetValue() {
        final var sp = set.spliterator();
        assertTrue(sp.tryAdvance(entry -> {
            assertEquals(Map.entry(KEY, VALUE), entry);
            entry.setValue(VALUE2);
            assertEquals(Map.entry(KEY, VALUE2), entry);
        }));
        assertEquals(Map.of(KEY, VALUE2), map);
    }
}
