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

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImmutableKeySetTest {
    private ImmutableKeySet<Object> set;

    @BeforeEach
    void before() {
        set = TrieMap.create().immutableSnapshot().createKeySet();
    }

    @Test
    void testIsEmpty() {
        assertTrue(set.isEmpty());
    }

    @Test
    void testSize() {
        assertEquals(0, set.size());
    }

    @Test
    void testClear() {
        assertThrows(UnsupportedOperationException.class, () -> set.clear());
    }

    @Test
    void testRemove() {
        assertThrows(UnsupportedOperationException.class, () -> set.remove(new Object()));
    }

    @Test
    void testRemoveAll() {
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(Collections.emptyList()));
    }

    @Test
    void testRetainAll() {
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(Collections.emptyList()));
    }

    @Test
    void testIterator() {
        assertFalse(set.iterator().hasNext());
    }
}
