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
 * Various implementation-specific constants shared across classes. Normally we would be deriving both
 * {@link #LEVEL_BITS} and {@link #MAX_DEPTH} from {@link #HASH_BITS} and size of {@link CNode#bitmap}, but that would
 * mean they would be runtime constants. We really want them to be compile-time constants. Hence we seed them manually
 * and assert the constants are correct.
 *
 * @author Robert Varga
 */
final class Constants {
    /**
     * Size of the hash function, in bits. This corresponds to {@link Object#hashCode()}'s size.
     */
    static final int HASH_BITS = Integer.SIZE;

    /**
     * Number of hash bits consumed in each CNode level. This corresponds to <code>log<sub>2</sub>(HASH_BITS)</code>.
     */
    static final int LEVEL_BITS = 5;

    /**
     * Maximum depth of a TrieMap. Maximum number of CNode levels. This corresponds to
     * {@code Math.ceil(HASH_BITS / LEVEL_BITS)}.
     */
    static final int MAX_DEPTH = 7;

    private Constants() {
        // Hidden on purpose
    }
}
