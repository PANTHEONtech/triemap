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

import java.util.Map;
import org.junit.jupiter.api.Test;

class LNodeEntryTest {
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE = "value";

    private final LNodeEntries<String, String> entry = LNodeEntries.of(KEY1, VALUE, KEY2, VALUE);

    @Test
    void testEntryUtil() {
        assertEquals(AbstractEntry.hashCode(KEY1, VALUE), entry.hashCode());
        assertEquals(AbstractEntry.toString(KEY1, VALUE), entry.toString());

        final var testEntry = Map.entry(KEY1, VALUE);
        assertEquals(AbstractEntry.equals(testEntry, KEY1, VALUE), entry.equals(testEntry));
    }

    @Test
    void testSetValue() {
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(null));
    }
}
