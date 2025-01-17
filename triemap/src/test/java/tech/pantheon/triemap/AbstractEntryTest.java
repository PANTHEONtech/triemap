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

class AbstractEntryTest {
    @Test
    void testEqual() {
        final var key = new Object();
        final var value = new Object();
        assertFalse(AbstractEntry.equals(null, key, value));
        assertFalse(AbstractEntry.equals(key, key, value));

        final var entry = Map.entry(key, value);
        assertTrue(AbstractEntry.equals(entry, key, value));
        assertFalse(AbstractEntry.equals(entry, value, value));
        assertFalse(AbstractEntry.equals(entry, key, key));
    }

    @Test
    void testHash() {
        final var key = new Object();
        final var value = new Object();
        assertEquals(key.hashCode() ^ value.hashCode(), AbstractEntry.hashCode(key, value));
    }

    @Test
    void testString() {
        assertEquals("foo=bar", AbstractEntry.toString("foo", "bar"));
    }
}
