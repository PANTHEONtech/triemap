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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SNodeTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final int HASH = 1337;

    private final SNode<String, String> snode = new SNode<>(KEY, VALUE, HASH);

    @Test
    void testCopyTombed() {
        final var tnode = new TNode<>(new CNode<>(new Gen()), snode);
        assertEquals(snode.hashCode(), tnode.hashCode());
        assertSame(snode.key(), tnode.key());
        assertSame(snode.value(), tnode.value());
    }

    @Test
    void testEntryUtil() {
        assertEquals(AbstractEntry.hashCode(KEY, VALUE), snode.hashCode());
        assertEquals(AbstractEntry.toString(KEY, VALUE), snode.toString());

        final var entry = Map.entry(KEY, VALUE);
        assertEquals(AbstractEntry.equals(entry, KEY, VALUE), snode.equals(entry));
    }
}
