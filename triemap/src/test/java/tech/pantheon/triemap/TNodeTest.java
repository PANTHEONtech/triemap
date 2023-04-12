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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TNodeTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final int HASH = 1337;

    private TNode<String, String> tnode;

    @BeforeEach
    void before() {
        tnode = new TNode<>(KEY, VALUE, HASH);
    }

    @Test
    void testCopyUntombed() {
        final SNode<String, String> snode = tnode.copyUntombed();
        assertEquals(tnode.hashCode(), snode.hashCode());
        assertSame(tnode.getKey(), snode.getKey());
        assertSame(tnode.getValue(), snode.getValue());
    }

    @Test
    void testSize() {
        assertEquals(1, tnode.trySize());
        assertEquals(1, tnode.size(null));
    }

    @Test
    void testEntryUtil() {
        assertEquals(EntryUtil.hash(KEY, VALUE), tnode.hashCode());
        assertEquals(EntryUtil.string(KEY, VALUE), tnode.toString());

        final Entry<String, String> entry = new SimpleImmutableEntry<>(KEY, VALUE);
        assertEquals(EntryUtil.equal(entry, KEY, VALUE), tnode.equals(entry));
    }
}
