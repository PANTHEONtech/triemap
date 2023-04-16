/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package tech.pantheon.triemap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConstantsTest {
    @Test
    void hashBits() throws Exception {
        // We assume Object.hashCode() is 32 bits
        assertEquals(int.class, Object.class.getDeclaredMethod("hashCode").getReturnType());
        assertEquals(Integer.SIZE, Constants.HASH_BITS);
    }

    @Test
    void levelBits() throws Exception {
        // CNode.bitmap can store 32 bits
        assertEquals(int.class, CNode.class.getDeclaredField("bitmap").getType());
        assertEquals((int) (Math.log(Integer.SIZE) / Math.log(2)), Constants.LEVEL_BITS);
    }

    @Test
    void maxDepth() throws Exception {
        assertEquals((int) Math.ceil((double)Constants.HASH_BITS / Constants.LEVEL_BITS), Constants.MAX_DEPTH);
    }
}
