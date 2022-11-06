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

import java.util.Map.Entry;

/**
 * Utility methods for implementing {@link Entry} contract.
 *
 * @author Robert Varga
 */
final class EntryUtil {
    private EntryUtil() {
        // Hidden on purpose
    }

    /**
     * Utility implementing {@link Entry#equals(Object)}.
     */
    static boolean equal(final Object obj, final Object key, final Object value) {
        if (!(obj instanceof Entry)) {
            return false;
        }

        final Entry<?, ?> entry = (Entry<?, ?>)obj;
        return key.equals(entry.getKey()) && value.equals(entry.getValue());
    }

    /**
     * Utility implementing {@link Entry#hashCode()}.
     */
    static int hash(final Object key, final Object value) {
        return key.hashCode() ^ value.hashCode();
    }

    /**
     * Utility implementing {@link Entry#toString()}.
     */
    static String string(final Object key, final Object value) {
        return key + "=" + value;
    }
}
