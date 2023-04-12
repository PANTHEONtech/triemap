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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EntryUtilTest {
    @Test
    void testEqual() {
        final Object key = new Object();
        final Object value = new Object();
        assertFalse(EntryUtil.entryEquals(null, key, value));
        assertFalse(EntryUtil.entryEquals(key, key, value));

        final var entry = Map.entry(key, value);
        assertTrue(EntryUtil.entryEquals(entry, key, value));
        assertFalse(EntryUtil.entryEquals(entry, value, value));
        assertFalse(EntryUtil.entryEquals(entry, key, key));
    }

    @Test
    void testHash() {
        final Object key = new Object();
        final Object value = new Object();
        assertEquals(key.hashCode() ^ value.hashCode(), EntryUtil.entryHashCode(key, value));
    }

    @Test
    void testString() {
        assertEquals("foo=bar", EntryUtil.entryToString("foo", "bar"));
    }

}
