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

import static java.util.Objects.requireNonNull;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;

/**
 * External serialization object for use with TrieMap objects. This hides the implementation details, such as object
 * hierarchy. It also makes handling read-only snapshots more elegant.
 *
 * @author Robert Varga
 */
final class SerializationProxy implements Externalizable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private transient TrieMap<Object, Object> map;
    private transient boolean readOnly;

    @SuppressWarnings("checkstyle:redundantModifier")
    public SerializationProxy() {
        // For Externalizable
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    SerializationProxy(final ImmutableTrieMap<?, ?> map, final boolean readOnly) {
        this.map = (TrieMap) requireNonNull(map);
        this.readOnly = readOnly;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(Equivalence.Equals.INSTANCE);
        out.writeInt(map.size());
        for (var e : map.entrySet()) {
            out.writeObject(e.getKey());
            out.writeObject(e.getValue());
        }
        out.writeBoolean(readOnly);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        final var eqObj = in.readObject();
        if (!(eqObj instanceof Equivalence)) {
            throw new InvalidObjectException("Expected Equivalence object instead of " + eqObj);
        }

        final var tmp = new MutableTrieMap<>();
        final int size = in.readInt();
        if (size < 0) {
            throw new StreamCorruptedException("Expected non-negative size instead of " + size);
        }

        for (int i = 0; i < size; ++i) {
            tmp.add(in.readObject(), in.readObject());
        }

        map = in.readBoolean() ? tmp.immutableSnapshot() : tmp;
    }

    @java.io.Serial
    private Object readResolve() throws ObjectStreamException {
        if (map == null) {
            throw new NotActiveException();
        }
        return map;
    }
}
