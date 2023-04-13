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

import org.junit.jupiter.api.Test;

class TestGetOrDefault {
    private final TrieMap<Object, Object> map = TrieMap.create();

    @Test
    void getOrDefaultNullKey() {
        final var defObject = new Object();
        assertThrows(NullPointerException.class, () -> map.getOrDefault(null, defObject));
    }

    @Test
    void getOrDefaultAbsent() {
        final var defObject = new Object();
        assertSame(defObject, map.getOrDefault(new Object(), defObject));
    }

    @Test
    void getOrDefaultPresent() {
        final var key = new Object();
        final var value = new Object();
        map.put(key, value);
        assertSame(value, map.getOrDefault(key, new Object()));
    }
}
