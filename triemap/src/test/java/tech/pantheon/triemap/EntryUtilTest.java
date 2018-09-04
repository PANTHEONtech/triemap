/*
 * (C) Copyright 2018 Pantheon Technologies, s.r.o. and others.
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
import static org.junit.Assert.assertTrue;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.junit.Test;

public class EntryUtilTest {
    @Test
    public void testEqual() {
        final Object key = new Object();
        final Object value = new Object();
        assertFalse(EntryUtil.equal(null, key, value));
        assertFalse(EntryUtil.equal(key, key, value));

        final Entry<Object, Object> entry = new SimpleImmutableEntry<>(key, value);
        assertTrue(EntryUtil.equal(entry, key, value));
        assertFalse(EntryUtil.equal(entry, value, value));
        assertFalse(EntryUtil.equal(entry, key, key));
    }

    @Test
    public void testHash() {
        final Object key = new Object();
        final Object value = new Object();
        assertEquals(key.hashCode() ^ value.hashCode(), EntryUtil.hash(key, value));
    }

    @Test
    public void testString() {
        assertEquals("foo=bar", EntryUtil.string("foo", "bar"));
    }

}
