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
 * Our {@link Entry} implementations are immutable by default.
 *
 * @author Robert Varga
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
sealed interface DefaultEntry<K, V> extends Entry<K, V> permits AbstractEntry, SNode, TNode {

    K key();

    @Override
    @Deprecated
    default K getKey() {
        return key();
    }

    V value();

    @Override
    @Deprecated
    default V getValue() {
        return value();
    }

    @Override
    default V setValue(final V value) {
        throw new UnsupportedOperationException();
    }
}
