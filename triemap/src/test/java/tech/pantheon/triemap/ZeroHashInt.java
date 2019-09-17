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

import java.util.StringJoiner;

/**
 * Utility key/value class which attacks the hasing function, causing all objects to be put into a single bucket.
 *
 * @author Robert Varga
 */
final class ZeroHashInt {
    private final int value;

    ZeroHashInt(final int value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ZeroHashInt && value == ((ZeroHashInt) obj).value;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ZeroHashInt.class.getSimpleName() + "{", "}")
                .add("value=" + value)
                .add("identity=" + System.identityHashCode(ZeroHashInt.class))
                .toString();
    }
}
