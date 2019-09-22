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
 * Specialized immutable iterator for use with {@link ImmutableEntrySet}.
 *
 * @author Robert Varga
 *
 * @param <K> the type of entry keys
 * @param <V> the type of entry values
 */
final class ImmutableIterator<K, V> extends AbstractIterator<K, V> {
    ImmutableIterator(final ImmutableTrieMap<K, V> map) {
        super(map);
    }

    @Override
    Entry<K, V> wrapEntry(final Entry<K, V> entry) {
        return entry;
    }
}
