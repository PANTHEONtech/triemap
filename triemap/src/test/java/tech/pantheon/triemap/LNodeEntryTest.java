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
import static org.junit.Assert.assertThrows;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;

public class LNodeEntryTest {
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE = "value";

    private LNodeEntry<String, String> entry;

    @Before
    public void before() {
        entry = LNodeEntries.map(KEY1, VALUE, KEY2, VALUE);
    }

    @Test
    public void testEntryUtil() {
        assertEquals(EntryUtil.hash(KEY1, VALUE), entry.hashCode());
        assertEquals(EntryUtil.string(KEY1, VALUE), entry.toString());

        final Entry<String, String> testEntry = new SimpleImmutableEntry<>(KEY1, VALUE);
        assertEquals(EntryUtil.equal(testEntry, KEY1, VALUE), entry.equals(testEntry));
    }

    @Test
    public void testSetValue() {
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(null));
    }
}
