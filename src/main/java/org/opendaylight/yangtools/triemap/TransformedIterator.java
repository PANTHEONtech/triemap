package org.opendaylight.yangtools.triemap;

import java.util.Iterator;

/**
 * An iterator that transforms a backing iterator; for internal use. This avoids the object overhead
 * of constructing a Function for internal methods.
 *
 * @author Louis Wasserman
 */
abstract class TransformedIterator<F, T> implements Iterator<T> {
    private final Iterator<? extends F> backingIterator;

    TransformedIterator(Iterator<? extends F> backingIterator) {
        this.backingIterator = CheckUtil.checkNotNull(backingIterator);
    }

    abstract T transform(F from);

    @Override
    public final boolean hasNext() {
        return backingIterator.hasNext();
    }

    @Override
    public final T next() {
        return transform(backingIterator.next());
    }

    @Override
    public final void remove() {
        backingIterator.remove();
    }

}
