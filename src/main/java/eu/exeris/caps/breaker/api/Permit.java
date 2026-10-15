package eu.exeris.caps.breaker.api;

/**
 * The result of {@link CircuitBreaker#acquirePermission()}: whether a call was admitted, and the state
 * generation it was admitted in.
 *
 * <p>Every state transition of a breaker starts a new generation. The outcome of a call is reported with the
 * permit it was admitted under; an outcome whose permit belongs to an earlier generation is counted in the
 * breaker's metrics and never drives a transition. A permit is single-use: a second outcome for the same
 * permit is ignored in the same way.
 *
 * @since 0.1.0
 */
public interface Permit {

    /**
     * @return {@code true} if the call was admitted, {@code false} if it must be short-circuited
     */
    boolean permitted();

    /**
     * @return the state generation the decision was taken in
     */
    long generation();
}
