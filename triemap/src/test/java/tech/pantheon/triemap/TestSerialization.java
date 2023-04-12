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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

class TestSerialization {
    @Test
    void testSerialization() throws IOException, ClassNotFoundException {
        var map = TrieMap.<String, String>create();

        map.put("dude-0", "tom");
        map.put("dude-1", "john");
        map.put("dude-3", "ravi");
        map.put("dude-4", "alex");

        var expected = map.immutableSnapshot();

        final var bos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(expected);
        }

        final TrieMap<?, ?> actual;
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            actual = assertInstanceOf(TrieMap.class, ois.readObject());
        }

        assertEquals(expected, actual);
    }
}
