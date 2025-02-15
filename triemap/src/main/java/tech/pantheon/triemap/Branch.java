/*
 * (C) Copyright 2025 PANTHEON.tech, s.r.o. and others.
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
 * A Branch: either an {@link INode} or an {@link SNode}.
 */
sealed interface Branch<K, V> permits INode, SNode {
    /**
     * Return the number of entries for the purposes of {@link CNode#size(ImmutableTrieMap)}.
     *
     * @param ct TrieMap reference
     * @return The actual number of entries
     */
    int elementSize(ImmutableTrieMap<K, V> ct);
}