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

import org.junit.Before;
import org.junit.Test;

public class ImmutableTrieMapTest {
    private ImmutableTrieMap<Object, Object> map;

    @Before
    public void before() {
        map = ImmutableTrieMap.create().immutableSnapshot();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClear() {
        map.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCompute() {
        map.compute(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testComputeIfAbsent() {
        map.computeIfAbsent(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testComputeIfPresent() {
        map.computeIfPresent(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMerge() {
        map.merge(null, null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPut() {
        map.put(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutAll() {
        map.putAll(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutIfAbsent() {
        map.putIfAbsent(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        map.remove(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveExact() {
        map.remove(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReplace() {
        map.replace(null, null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReplaceExact() {
        map.replace(null, null, null);
    }
}
