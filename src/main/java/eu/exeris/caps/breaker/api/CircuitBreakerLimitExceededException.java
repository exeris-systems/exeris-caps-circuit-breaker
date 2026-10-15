package eu.exeris.caps.breaker.api;

/**
 * Thrown when a registry is asked to create a named breaker beyond its maximum. Breakers are never evicted to
 * make room, because evicting one loses the state of an open circuit.
 *
 * @since 0.1.0
 */
public final class CircuitBreakerLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String breakerName;
    private final int limit;

    public CircuitBreakerLimitExceededException(String breakerName, int limit) {
        super("Circuit breaker limit of " + limit + " reached; cannot create '" + breakerName + "'");
        this.breakerName = breakerName;
        this.limit = limit;
    }

    /** @return the name that could not be created */
    public String breakerName() {
        return breakerName;
    }

    /** @return the registry's maximum number of breakers */
    public int limit() {
        return limit;
    }
}
