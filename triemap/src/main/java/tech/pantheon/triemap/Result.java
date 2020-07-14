/*
 * (C) Copyright 2020 PANTHEON.tech, s.r.o. and others.
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
 * Insertion result, similar to {@link java.util.Optional}, except heavily customized for our use.
 */
final class Result<T> {
    private static final @NonNull Result<?> EMPTY = new Result<>(null);

    private final T value;

    private Result(final T value) {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    static <T> @NonNull Result<T> empty() {
        return (Result<T>) EMPTY;
    }

    static <T> @NonNull Result<T> of(final LNodeEntry<?, T> entry) {
        return new Result<>(entry.getValue());
    }

    static <T> @NonNull Result<T> of(final SNode<?, T> snode) {
        return new Result<>(snode.value);
    }

    boolean isPresent() {
        return value != null;
    }

    T orNull() {
        return value;
    }
}
