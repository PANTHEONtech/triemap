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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MutableIteratorTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";

    private MutableIterator<String, String> it;

    @BeforeEach
    void before() {
        final MutableTrieMap<String, String> map = TrieMap.create();
        map.put(KEY, VALUE);
        it = map.iterator();
    }

    @Test
    void testEntryUtil() {
        final Entry<String, String> entry = it.next();

        assertEquals(EntryUtil.hash(KEY, VALUE), entry.hashCode());
        assertEquals(EntryUtil.string(KEY, VALUE), entry.toString());

        final Entry<String, String> testEntry = new SimpleImmutableEntry<>(KEY, VALUE);
        assertEquals(EntryUtil.equal(testEntry, KEY, VALUE), entry.equals(entry));
    }

    @Test
    void testRemoveWithoutNext() {
        assertThrows(IllegalStateException.class, () -> it.remove());
    }
}
