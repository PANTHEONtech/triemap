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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MutableSpliteratorTest {

    @Test
    void spliteratorTrySplit() {

        final var map = getMutableMap(1);
        final var sit = map.entrySet().spliterator();
        assertTrue(sit.tryAdvance(e -> e.setValue("Updated")));
        assertEquals("Updated", map.get("1"));
    }

    private static TrieMap<String, String> getMutableMap(int size) {
        final var map = TrieMap.<String, String>create();
        IntStream.range(1, size + 1).forEach(i -> map.put(String.valueOf(i), "Value" + i));
        return map;
    }

}
