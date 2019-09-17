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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;

public class SNodeTest {
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final int HASH = 1337;

    private SNode<String, String> snode;

    @Before
    public void before() {
        snode = new SNode<>(KEY, VALUE, HASH);
    }

    @Test
    public void testCopyTombed() {
        final TNode<String, String> tnode = snode.copyTombed();
        assertEquals(snode.hashCode(), tnode.hashCode());
        assertSame(snode.getKey(), tnode.getKey());
        assertSame(snode.getValue(), tnode.getValue());
    }

    @Test
    public void testEntryUtil() {
        assertEquals(EntryUtil.hash(KEY, VALUE), snode.hashCode());
        assertEquals(EntryUtil.string(KEY, VALUE), snode.toString());

        final Entry<String, String> entry = new SimpleImmutableEntry<>(KEY, VALUE);
        assertEquals(EntryUtil.equal(entry, KEY, VALUE), snode.equals(entry));
    }
}
