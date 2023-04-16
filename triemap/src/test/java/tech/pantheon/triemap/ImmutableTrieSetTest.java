/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package tech.pantheon.triemap;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ImmutableTrieSetTest {
    private final ImmutableTrieSet<Object> set = ImmutableTrieSet.create().immutableSnapshot();

    @Test
    void immutableSnapshotIsSame() {
        assertSame(set, set.immutableSnapshot());
    }

    @Test
    void addThrows() {
        final var obj = new Object();
        assertThrows(UnsupportedOperationException.class, () -> set.add(obj));

        final var coll = List.of();
        assertThrows(UnsupportedOperationException.class, () -> set.addAll(coll));
    }

    @Test
    void clearThrows() {
        assertThrows(UnsupportedOperationException.class, () -> set.clear());
    }

    @Test
    void removeThrows() {
        final var obj = new Object();
        assertThrows(UnsupportedOperationException.class, () -> set.remove(obj));

        final var coll = List.of();
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(coll));
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(coll));
    }

    @Test
    void removeIfThrows() {
        final Predicate<Object> pred = obj -> true;
        assertThrows(UnsupportedOperationException.class, () -> set.removeIf(pred));
    }
}
