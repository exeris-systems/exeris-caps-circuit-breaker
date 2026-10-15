package eu.exeris.caps.breaker.api;

/**
 * A manual action of {@link CircuitBreakerAdmin}.
 *
 * @since 0.1.0
 */
public enum AdminAction {
    /** Hold the breaker open. */
    FORCE_OPEN,
    /** Bypass the breaker: always permit. */
    DISABLE,
    /** Return the breaker to automatic operation in {@link CircuitState#CLOSED} with fresh counters. */
    RELEASE
}
