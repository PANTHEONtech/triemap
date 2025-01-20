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

import org.eclipse.jdt.annotation.NonNull;

/**
 * A single entry in {@link LNodeEntries}, implements {@link DefaultEntry} in order to prevent instantiation of objects
 * for iteration.
 *
 * @author Robert Varga
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
abstract sealed class LNodeEntry<K, V> extends AbstractEntry<K, V> permits LNodeEntries {
    private final K key;
    private final V value;

    LNodeEntry(final K key, final V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public final K key() {
        return key;
    }

    @Override
    public final V value() {
        return value;
    }

    final @NonNull Result<V> toResult() {
        return new Result<>(value);
    }
}
