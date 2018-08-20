package org.opendaylight.yangtools.triemap;

final class CheckUtil {

    /**
     * Ensures that {@code reference} is non-null, throwing a {@code IllegalStateException} with a custom
     * message otherwise.
     *
     * @return {@code reference}, guaranteed to be non-null, for convenience
     * @throws IllegalStateException if {@code reference} is {@code null}     *
     */
    static <T> T verifyNotNull(T reference) {
        verify(reference != null, "expected a non-null reference");
        return reference;
    }
    /**
     * Ensures that {@code expression} is {@code true}, throwing a {@code VerifyException} with no
     * message otherwise.
     *
     * @throws IllegalStateException if {@code expression} is {@code false}
     */
     static void verify(boolean expression) {
        if(!expression){
            throw new IllegalStateException();
        }
     }
    static void verify(boolean expression, String errorMessage) {
        if(!expression){
            throw new IllegalStateException(errorMessage);
        }
    }
    /**
     * Ensures that an object reference passed as a parameter to the calling method is not null.
     *
     * @param reference an object reference
     * @return the non-null reference that was validated
     * @throws NullPointerException if {@code reference} is null
     */
     static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }



}
