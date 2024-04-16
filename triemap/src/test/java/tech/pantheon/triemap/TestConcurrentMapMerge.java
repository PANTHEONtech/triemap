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
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class TestConcurrentMapMerge {
    private static final int COUNT = 50 * 1000;

    @Test
    void testConcurrentMapMergeWhenValueAbsent() {
        final var map = TrieMap.create();

        for (int i = 0; i < COUNT; i++) {
            final var newVal = Integer.toString(i + 10);
            assertEquals(newVal, map.merge(i, newVal, (ov, nv) -> fail("Should not have been called")));
        }
    }

    @Test
    void testConcurrentMapMergeWhenValuePresent() {
        final var map = TrieMap.create();

        for (int i = 0; i < COUNT; i++) {
            final var newVal = Integer.toString(i + 10);
            map.put(i, newVal);
            assertEquals(newVal + newVal, map.merge(i, newVal, (ov, nv) -> "" + ov + nv));
        }
    }
}
