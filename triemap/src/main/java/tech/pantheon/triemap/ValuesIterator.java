/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package tech.pantheon.triemap;

import static java.util.Objects.requireNonNull;

import java.util.Iterator;

final class ValuesIterator<V> implements Iterator<V> {
    private final AbstractIterator<?, V> delegate;

    ValuesIterator(final AbstractIterator<?, V> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public V next() {
        return delegate.next().getValue();
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
