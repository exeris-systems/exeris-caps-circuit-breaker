package eu.exeris.caps.breaker.api;

import java.time.Instant;

/**
 * One manual action taken through {@link CircuitBreakerAdmin}, as reported to an {@link AdminListener}.
 *
 * @param breakerName   the breaker acted on
 * @param action        what was done
 * @param previousState the state immediately before the action
 * @param newState      the state immediately after the action
 * @param timestamp     when the action took effect
 * @param actor         the operator or system that asked for it, as passed by the caller
 * @since 0.1.0
 */
public record AdminEvent(String breakerName, AdminAction action, CircuitState previousState,
                         CircuitState newState, Instant timestamp, String actor) {
}
