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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class TestCNodeFlagCollision {
    @Test
    void testCNodeFlagCollision() {
        final var map = TrieMap.<Integer, Object>create();
        final Integer z15169 = 15169;
        final Integer z28336 = 28336;

        assertNull(map.get(z15169));
        assertNull(map.get(z28336));

        map.put(z15169, z15169);
        assertSame(z15169, map.get(z15169));
        assertNull(map.get(z28336));

        map.put(z28336, z28336);
        assertSame(z15169, map.get(z15169));
        assertSame(z28336, map.get(z28336));

        map.remove(z15169);

        assertNull(map.get(z15169));
        assertSame(z28336, map.get(z28336));

        map.remove(z28336);

        assertNull(map.get(z15169));
        assertNull(map.get(z28336));
    }
}
