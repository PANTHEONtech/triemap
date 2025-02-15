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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tech.pantheon.triemap.INode.FailedGcas;

class INodeTest {
    @Test
    void testInvalidElement() {
        assertThrows(VerifyException.class, () -> INode.invalidElement(null));
    }

    @Test
    void testFailedGcasToString() {
        final var tnode = new TNode<>(new CNode<>(new Gen()), new Object(), new Object(), 123);
        assertEquals("FailedNode(" + tnode + ")", new FailedGcas<>(tnode).toString());
    }
}
