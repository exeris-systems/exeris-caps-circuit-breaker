package eu.exeris.caps.breaker.api;

import eu.exeris.caps.breaker.internal.DefaultCircuitBreakerAdmin;
import eu.exeris.caps.breaker.internal.DefaultCircuitBreakerRegistry;
import eu.exeris.caps.breaker.internal.LockFreeCircuitBreaker;

/**
 * Static factory methods for creating circuit breakers and registries.
 *
 * @since 0.1.0
 */
public final class CircuitBreakers {

    private CircuitBreakers() {
    }

    /**
     * Creates a new empty {@link CircuitBreakerRegistry} with default configuration.
     */
    public static CircuitBreakerRegistry newRegistry() {
        return new DefaultCircuitBreakerRegistry();
    }

    /**
     * Creates a new empty {@link CircuitBreakerRegistry} with custom default configuration.
     */
    public static CircuitBreakerRegistry newRegistry(CircuitBreakerConfig defaultConfig) {
        return new DefaultCircuitBreakerRegistry(defaultConfig);
    }

    /**
     * Creates a new empty {@link CircuitBreakerRegistry} with custom default configuration and a maximum
     * number of breakers.
     *
     * @param maxBreakers the most breakers the registry holds; at least 1
     */
    public static CircuitBreakerRegistry newRegistry(CircuitBreakerConfig defaultConfig, int maxBreakers) {
        return new DefaultCircuitBreakerRegistry(defaultConfig, maxBreakers);
    }

    /**
     * Creates the {@link CircuitBreakerAdmin} for a registry made by this factory. The caller decides who is
     * handed it; every action is reported to {@code listener}.
     *
     * @throws IllegalArgumentException if the registry was not made by {@link #newRegistry}
     */
    public static CircuitBreakerAdmin newAdmin(CircuitBreakerRegistry registry, AdminListener listener) {
        return new DefaultCircuitBreakerAdmin(registry, listener);
    }

    /**
     * Creates a standalone {@link CircuitBreaker} with default configuration.
     */
    public static CircuitBreaker of(String name) {
        return of(name, CircuitBreakerConfig.DEFAULT);
    }

    /**
     * Creates a standalone {@link CircuitBreaker} with custom configuration.
     */
    public static CircuitBreaker of(String name, CircuitBreakerConfig config) {
        return new LockFreeCircuitBreaker(name, config);
    }
}
