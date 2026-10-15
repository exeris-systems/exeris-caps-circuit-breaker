package eu.exeris.caps.breaker.api;

/**
 * Lifecycle states of a {@link CircuitBreaker}.
 *
 * <p>{@link #CLOSED}, {@link #OPEN} and {@link #HALF_OPEN} are the automatic states: the breaker moves between
 * them on its own from the recorded outcomes. {@link #FORCED_OPEN} and {@link #DISABLED} are manual: the
 * breaker never enters or leaves them automatically, and only a manual action leaves them; {@link CircuitBreakerAdmin#release} is the only way back to automatic operation.
 *
 * @since 0.1.0
 */
public enum CircuitState {
    /** Normal operation: requests are routed directly to the upstream. */
    CLOSED,
    /** Upstream failure detected: all requests are short-circuited until the wait period has passed. */
    OPEN,
    /** Probationary trial: a limited number of requests are permitted to evaluate upstream health. */
    HALF_OPEN,
    /** Held open by an operator: every request is short-circuited, with no automatic way out. */
    FORCED_OPEN,
    /** Bypassed by an operator: every request is permitted and outcomes never change the state. */
    DISABLED
}
