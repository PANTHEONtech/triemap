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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TestHashCollisionsRemoveIterator {
    private static final int COUNT = 50000;

    @Test
    void testHashCollisionsRemoveIterator() {
        final var bt = TrieMap.create();
        for (int j = 0; j < COUNT; j++) {
            bt.put(Integer.valueOf(j), Integer.valueOf(j));
        }

        final var list = new ArrayList<>(COUNT);
        final var it = bt.entrySet().iterator();
        while (it.hasNext()) {
            list.add(it.next().getKey());
            it.remove();
        }

        assertEquals(0, bt.size());
        assertTrue(bt.isEmpty());
        assertEquals(COUNT, list.size());
    }
}
