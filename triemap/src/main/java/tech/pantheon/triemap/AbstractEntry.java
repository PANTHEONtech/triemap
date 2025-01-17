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
 * Base class for things that need to conform to {@link Entry} contract.
 *
 * @author Robert Varga
 */
abstract sealed class AbstractEntry<K, V> implements Entry<K, V> permits LNodeEntry, MutableIterator.MutableEntry {
    @Override
    public final int hashCode() {
        return hashCode(getKey(), getValue());
    }

    static final int hashCode(final Object key, final Object value) {
        return key.hashCode() ^ value.hashCode();
    }

    @Override
    public final boolean equals(final Object obj) {
        return equals(obj, getKey(), getValue());
    }

    static final boolean equals(final Object obj, final Object key, final Object value) {
        return obj instanceof Entry<?, ?> entry && key.equals(entry.getKey()) && value.equals(entry.getValue());
    }

    @Override
    public final String toString() {
        return toString(getKey(), getValue());
    }

    static final String toString(final Object key, final Object value) {
        return key + "=" + value;
    }
}
