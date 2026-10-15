package eu.exeris.caps.breaker.api;

import java.util.Map;
import java.util.Optional;

/**
 * Manual control of circuit breakers, provided separately from the call-site {@link CircuitBreaker} so that
 * changing a breaker's state is a capability a composition grants, not something every caller holds.
 *
 * <p>Whoever is not handed this service cannot change a breaker's state. Every action is reported to the
 * {@link AdminListener} and logged through {@code System.Logger}. {@link CircuitState#FORCED_OPEN} and
 * {@link CircuitState#DISABLED} are left only by a manual action: {@link #forceOpen} and {@link #disable} replace one with the other, and {@link #release} is the only way back to automatic operation.
 *
 * @since 0.1.0
 */
public interface CircuitBreakerAdmin {

    /**
     * Holds a breaker open: every call is short-circuited until {@link #release}.
     *
     * @param breakerName the breaker
     * @param actor       who asks, recorded in the event; not blank
     * @return the event that was reported
     * @throws java.util.NoSuchElementException if no breaker has that name
     */
    AdminEvent forceOpen(String breakerName, String actor);

    /**
     * Bypasses a breaker: every call is permitted and outcomes never change its state, until {@link #release}.
     *
     * @param breakerName the breaker
     * @param actor       who asks, recorded in the event; not blank
     * @return the event that was reported
     * @throws java.util.NoSuchElementException if no breaker has that name
     */
    AdminEvent disable(String breakerName, String actor);

    /**
     * Returns a breaker to automatic operation: {@link CircuitState#CLOSED} with fresh counters. Valid from
     * any state.
     *
     * @param breakerName the breaker
     * @param actor       who asks, recorded in the event; not blank
     * @return the event that was reported
     * @throws java.util.NoSuchElementException if no breaker has that name
     */
    AdminEvent release(String breakerName, String actor);

    /**
     * @param breakerName the breaker
     * @return its current state, or empty if no breaker has that name
     */
    Optional<CircuitState> state(String breakerName);

    /**
     * @return the current state of every breaker, by name
     */
    Map<String, CircuitState> states();
}
