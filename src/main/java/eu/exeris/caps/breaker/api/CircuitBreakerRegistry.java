package eu.exeris.caps.breaker.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry holding named {@link CircuitBreaker} instances.
 *
 * <p>The number of breakers is bounded by {@link #maxBreakers()}, {@value #DEFAULT_MAX_BREAKERS} by default.
 * Creating one beyond the limit throws {@link CircuitBreakerLimitExceededException} and increments
 * {@link #limitRejections()}; no breaker is ever evicted, because evicting one loses the state of an open
 * circuit. Names must therefore come from configuration or route definitions, never from request data such
 * as a path, a header or a client-supplied host.
 *
 * @since 0.1.0
 */
public interface CircuitBreakerRegistry extends AutoCloseable {

    /** The default maximum number of named breakers. */
    int DEFAULT_MAX_BREAKERS = 1024;

    /**
     * @return the maximum number of breakers this registry holds
     */
    int maxBreakers();

    /**
     * @return how many times a creation was refused because the limit was reached
     */
    long limitRejections();

    /**
     * Gets or creates a circuit breaker using the default configuration.
     *
     * @param name unique circuit name, taken from configuration, never from request data
     * @return the circuit breaker
     * @throws CircuitBreakerLimitExceededException if the name is new and the limit is reached
     */
    CircuitBreaker getOrCreate(String name);

    /**
     * Alias for {@link #getOrCreate(String)}.
     */
    default CircuitBreaker circuitBreaker(String name) {
        return getOrCreate(name);
    }

    /**
     * Gets or creates a circuit breaker with a custom configuration.
     *
     * @param name   unique circuit name, taken from configuration, never from request data
     * @param config configuration parameters
     * @return the circuit breaker
     * @throws CircuitBreakerLimitExceededException if the name is new and the limit is reached
     */
    CircuitBreaker getOrCreate(String name, CircuitBreakerConfig config);

    /**
     * Alias for {@link #getOrCreate(String, CircuitBreakerConfig)}.
     */
    default CircuitBreaker circuitBreaker(String name, CircuitBreakerConfig config) {
        return getOrCreate(name, config);
    }

    /**
     * Finds an existing circuit breaker by name.
     *
     * @param name circuit name
     * @return an Optional containing the circuit breaker if present
     */
    Optional<CircuitBreaker> find(String name);

    /**
     * @return all registered circuit breakers
     */
    Collection<CircuitBreaker> all();

    @Override
    void close();
}
