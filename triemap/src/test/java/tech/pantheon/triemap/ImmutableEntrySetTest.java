/*
 * (C) Copyright 2018 Pantheon Technologies, s.r.o. and others.
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
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class ImmutableEntrySetTest {
    private ImmutableEntrySet<Object, Object> set;

    @Before
    public void before() {
        set = TrieMap.create().immutableSnapshot().createEntrySet();
    }

    @Test
    public void testIsEmpty() {
        assertTrue(set.isEmpty());
    }

    @Test
    public void testSize() {
        assertEquals(0, set.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClear() {
        set.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        set.remove(new Object());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveAll() {
        set.removeAll(Collections.emptyList());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRetainAll() {
        set.retainAll(Collections.emptyList());
    }
}
