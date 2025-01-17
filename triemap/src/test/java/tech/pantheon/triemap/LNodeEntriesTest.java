/*
 * (C) Copyright 2016 PANTHEON.tech, s.r.o. and others.
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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LNodeEntriesTest {
    private LNodeEntries<Integer, Boolean> map = LNodeEntries.map(1, TRUE, 2, TRUE);

    @Test
    void testReplaceInvalid() {
        final var lnode = new LNodeEntries.Single<>(1, TRUE);
        final var ex = assertThrows(VerifyException.class, () -> map.replace(lnode, FALSE));
        assertEquals("Failed to find entry 1=true", ex.getMessage());
    }

    @Test
    void testReplaceHead() {
        final var modified = map.replace(map, FALSE);
        assertEquals(map.next(), modified.next());
        assertEquals(Map.entry(1, FALSE), modified);

        final var trimmed = modified.remove(modified);
        assertEquals(Map.entry(2, TRUE), trimmed.replace(trimmed, TRUE));
    }

    @Test
    void testReplaceTail() {
        final var modified = map.replace(map.next(), FALSE);
        assertEquals(map, modified.next());
        assertEquals(Map.entry(2, FALSE), modified);

        final var trimmed = modified.remove(modified);
        assertEquals(Map.entry(1, TRUE), trimmed.replace(trimmed, TRUE));
    }

    @Test
    void testRemoveHead() {
        final var modified = map.remove(map);
        assertSame(map.next(), modified);
        assertNull(modified.remove(modified));
    }

    @Test
    void testRemoveTail() {
        final LNodeEntries<Integer, Boolean> modified = map.remove(map.next());
        assertEquals(map, modified);
        assertNull(modified.remove(modified));
    }

    /**
     * Test if Listmap.get() does not cause stack overflow.
     */
    @Test
    void testGetOverflow() {
        // 30K seems to be enough to trigger the problem locally
        for (int i = 3; i < 30000; ++i) {
            map = map.insert(i, TRUE);
        }

        assertNull(map.findEntry(0));
    }
}
