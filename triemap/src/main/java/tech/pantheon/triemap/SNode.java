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
import org.eclipse.jdt.annotation.Nullable;

record SNode<K, V>(K key, V value, int hc) implements Branch<K, V>, EntryNode<K, V> {
    SNode(final TNode<K, V> tn) {
        this(tn.key, tn.value, tn.hc);
    }

    @Nullable V lookup(final int otherHc, final K otherKey) {
        return matches(otherHc, otherKey) ? value : null;
    }

    boolean matches(final int otherHc, final Object otherKey) {
        return hc == otherHc && otherKey.equals(key);
    }

    @NonNull Result<V> toResult() {
        return new Result<>(value);
    }

    @Override
    public int hashCode() {
        return AbstractEntry.hashCode(key, value);
    }

    @Override
    public boolean equals(final Object obj) {
        return AbstractEntry.equals(obj, key, value);
    }

    @Override
    public String toString() {
        return AbstractEntry.toString(key, value);
    }
}
