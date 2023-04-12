/*
 * (C) Copyright 2017 PANTHEON.tech, s.r.o. and others.
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

/**
 * Various implementation-specific constants shared across classes.
 *
 * @author Robert Varga
 */
final class Constants {
    private Constants() {
        // Hidden on purpose
    }

    /**
     * Size of the hash function, in bits.
     */
    static final int HASH_BITS = Integer.SIZE;

    /**
     * Size of the CNode bitmap, in bits.
     */
    static final int BITMAP_BITS = Integer.SIZE;

    /**
     * Number of hash bits consumed in each CNode level.
     */
    static final int LEVEL_BITS = 5;

    /**
     * Maximum depth of a TrieMap.
     */
    static final int MAX_DEPTH = 7;

    /*
     * Normally we would be deriving both LEVEL_BITS and MAX_DEPTH from HASH_BITS and BITMAP_BITS, but that would mean
     * they would be runtime constants. We really want them to be compile-time constants. Hence we seed them manually
     * and assert the constants are correct.
     */
    static {
        final int expectedBits = (int) (Math.log(BITMAP_BITS) / Math.log(2));
        if (LEVEL_BITS != expectedBits) {
            throw new AssertionError("BITMAP_BITS=%s implies LEVEL_BITS=%s, but %s found".formatted(BITMAP_BITS,
                expectedBits, LEVEL_BITS));
        }

        final int expectedDepth = (int) Math.ceil((double)HASH_BITS / LEVEL_BITS);
        if (MAX_DEPTH != expectedDepth) {
            throw new AssertionError("HASH_BITS=%s and LEVEL_BITS=%s implies MAX_DEPTH=%s, but %s found".formatted(
                HASH_BITS, LEVEL_BITS, expectedDepth, MAX_DEPTH));
        }
    }
}
