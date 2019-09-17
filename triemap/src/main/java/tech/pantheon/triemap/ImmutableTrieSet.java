/*
 * (C) Copyright 2019 PANTHEON.tech, s.r.o. and others.
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
 * An immutable TrieSet. Does not allow modifications.
 *
 * @param <E> the type of elements maintained by this set
 * @author Robert Varga
 */
public final class ImmutableTrieSet<E> extends TrieSet<E> {
    private static final long serialVersionUID = 1L;

    ImmutableTrieSet(final ImmutableTrieMap<E, Boolean> map) {
        super(map);
    }

    @Override
    public ImmutableTrieSet<E> immutableSnapshot() {
        return this;
    }
}
