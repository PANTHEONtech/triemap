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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class VerifyExceptionTest {
    @Test(expected = VerifyException.class)
    public void testThrowIfNullSimple() {
        VerifyException.throwIfNull(null);
    }

    @Test
    public void testThrowIfNullSame() {
        final Object obj = new Object();
        assertSame(obj, VerifyException.throwIfNull(obj));
    }

    @Test
    public void testThrowIfMessage() {
        try {
            VerifyException.throwIf(true, "foo %s", "foo");
            fail("Expected exception");
        } catch (VerifyException e) {
            assertEquals("foo foo", e.getMessage());
        }
    }

    @Test(expected = NullPointerException.class)
    public void testThrowIfTrueNullFormat() {
        VerifyException.throwIf(true, null);
    }
}
