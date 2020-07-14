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

import java.io.Serializable;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Internal equivalence class, similar to com.google.common.base.Equivalence, but explicitly not handling
 * nulls. We use equivalence only for keys, which are guaranteed to be non-null.
 *
 * @author Robert Varga
 */
@NonNullByDefault
abstract class Equivalence<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final class Equals extends Equivalence<Object> {
        private static final long serialVersionUID = 1L;

        static final Equals INSTANCE = new Equals();

        @Override
        boolean equivalent(final Object first, final Object second) {
            return first.equals(second);
        }

        @Override
        Object readResolve() {
            return INSTANCE;
        }
    }

    static Equivalence<Object> equals() {
        return Equals.INSTANCE;
    }

    final int hash(final T obj) {
        int hash = obj.hashCode();

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        hash ^= hash >>> 20 ^ hash >>> 12;
        hash ^= hash >>> 7 ^ hash >>> 4;
        return hash;
    }

    abstract boolean equivalent(T first, T second);

    abstract Object readResolve();
}
