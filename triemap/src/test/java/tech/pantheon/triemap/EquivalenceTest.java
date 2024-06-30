/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package tech.pantheon.triemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class EquivalenceTest {
    @Test
    void readResolveWorks() throws Exception {
        final var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(Equivalence.Equals.INSTANCE);
        }

        final var bytes = baos.toByteArray();
        assertEquals("""
            aced000573720028746563682e70616e7468656f6e2e747269656d61702e4571756976616c656e636524457175616c7300000000000\
            0000102000078720021746563682e70616e7468656f6e2e747269656d61702e4571756976616c656e63650000000000000001020000\
            7870""", HexFormat.of().formatHex(bytes));

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            assertSame(Equivalence.Equals.INSTANCE, ois.readObject());
        }
    }
}
