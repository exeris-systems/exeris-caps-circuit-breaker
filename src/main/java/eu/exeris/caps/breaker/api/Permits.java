package eu.exeris.caps.breaker.api;

/**
 * Reads the permit that {@link CircuitBreaker#acquirePermission()} returns.
 *
 * <p>A permit is a primitive {@code long}, so admitting a call allocates nothing. It records whether the call
 * was admitted, which breaker admitted it and the state generation it was admitted in, and it names one
 * admission. Every state transition of a breaker starts a new generation. The outcome of a call is reported
 * with the permit it was admitted under; an outcome whose permit belongs to an earlier generation is counted
 * in the breaker's metrics and never drives a transition. A permit is single-use: a second outcome for the
 * same permit, an outcome for a permit that was not admitted and an outcome for a permit of another breaker
 * are ignored in the same way.
 *
 * <p>A breaker remembers its most recent admissions (1024 of them) so that it can tell a second outcome from
 * a first one. An outcome reported for an admission that has since been displaced by that many newer ones is
 * treated as already completed. A permit is only ever meaningful to the breaker that issued it, and only as
 * the value returned: its other bits are not a contract.
 *
 * @since 0.1.0
 */
public final class Permits {

    /** The number of bits of the state generation that a permit carries. */
    public static final int GENERATION_BITS = 20;

    private static final int GENERATION_SHIFT = 23;
    private static final long GENERATION_MASK = (1L << GENERATION_BITS) - 1;

    private Permits() {
    }

    /**
     * @param permit a value returned by {@link CircuitBreaker#acquirePermission()}
     * @return {@code true} if the call was admitted, {@code false} if it must be short-circuited
     */
    public static boolean permitted(long permit) {
        return permit < 0;
    }

    /**
     * @param permit a value returned by {@link CircuitBreaker#acquirePermission()}
     * @return the state generation the decision was taken in, modulo 2<sup>{@value #GENERATION_BITS}</sup>;
     *         for a breaker with fewer transitions than that it equals {@link CircuitBreaker#generation()} of
     *         that moment
     */
    public static long generation(long permit) {
        return (permit >>> GENERATION_SHIFT) & GENERATION_MASK;
    }
}
