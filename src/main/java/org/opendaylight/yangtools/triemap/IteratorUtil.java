package org.opendaylight.yangtools.triemap;

import java.util.Iterator;
import java.util.function.Function;

final class IteratorUtil {
    /**
     * Returns a view containing the result of applying {@code function} to each element of {@code
     * fromIterator}.
     *
     * <p>The returned iterator supports {@code remove()} if {@code fromIterator} does. After a
     * successful {@code remove()} call, {@code fromIterator} no longer contains the corresponding
     * element.
     */
    static <F, T> Iterator<T> transformIterator(
            final Iterator<F> fromIterator, final Function<? super F, ? extends T> function) {
        CheckUtil.checkNotNull(function);
        return new TransformedIterator<F, T>(fromIterator) {
            @Override
            T transform(F from) {
                return function.apply(from);
            }
        };
    }
}
