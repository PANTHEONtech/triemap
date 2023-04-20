/*
 * (C) Copyright 2023 PANTHEON.tech, s.r.o. and others.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

public class EntrySetSpliteratorTest {

    private static final String VALUE_PREFIX = "value-";
    private static final String UPDATED_PREFIX = "updated-";

    private static final Consumer<Map.Entry<String, String>> ENTRY_UPDATER = entry -> {
        if (entry.getValue().equals(VALUE_PREFIX + entry.getKey())) {
            entry.setValue(UPDATED_PREFIX + entry.getKey());
        } else {
            throw new IllegalStateException("entry with key " + entry.getKey() + " is already updated");
        }
    };

    @Test
    void spliteratorMutableEntryUpdate() {
        final var map = getMutableMap(1);
        final var spliterator = map.entrySet().spliterator();
        assertTrue(spliterator.tryAdvance(ENTRY_UPDATER));
        assertMapUpdated(map);
    }

    @Test
    void spliteratorImmutableEntryUpdate() {
        final var map = getMutableMap(1).immutableSnapshot();
        final var spliterator = map.entrySet().spliterator();
        assertThrows(UnsupportedOperationException.class, () -> spliterator.tryAdvance(ENTRY_UPDATER));
    }

    @Test
    void spliteratorTrySplit() {
        final var map = getMutableMap(2050);
        final var spliterator0 = map.entrySet().spliterator();
        assertEquals(2050, spliterator0.estimateSize());

        // trySplit divides the remaining scope to two parts if remaining >= 1024
        final var spliterator1 = spliterator0.trySplit();
        assertNotNull(spliterator1);
        assertEquals(1025, spliterator0.estimateSize());
        assertEquals(1025, spliterator1.estimateSize());

        final var spliterator2 = spliterator0.trySplit();
        final var spliterator3 = spliterator1.trySplit();
        assertNotNull(spliterator2);
        assertNotNull(spliterator3);
        assertEquals(513, spliterator0.estimateSize());
        assertEquals(513, spliterator1.estimateSize());
        assertEquals(512, spliterator2.estimateSize());
        assertEquals(512, spliterator3.estimateSize());

        assertNull(spliterator0.trySplit());
        assertNull(spliterator1.trySplit());
        assertNull(spliterator2.trySplit());
        assertNull(spliterator3.trySplit());
    }

    @Test
    void concurrentUpdate() {
        final var map = getMutableMap(10000);
        StreamSupport.stream(map.entrySet().spliterator(), true).forEach(ENTRY_UPDATER);
        assertMapUpdated(map);
    }

    @Test
    void singleThreadUpdate() {
        final var map = getMutableMap(10000);
        StreamSupport.stream(map.entrySet().spliterator(), false).forEach(ENTRY_UPDATER);
        assertMapUpdated(map);
    }

    private static TrieMap<String, String> getMutableMap(int size) {
        final var map = TrieMap.<String, String>create();
        IntStream.range(1, size + 1).forEach(i -> map.put(String.valueOf(i), VALUE_PREFIX + i));
        return map;
    }

    private static void assertMapUpdated(final TrieMap<String, String> map) {
        for (var entry : map.createEntrySet()) {
            assertEquals(UPDATED_PREFIX + entry.getKey(), entry.getValue());
        }
    }
}
