package eu.exeris.caps.breaker.api;

/**
 * Receives every manual action taken through {@link CircuitBreakerAdmin}, so that a composition can write it
 * to an audit trail.
 *
 * <p>The listener is called on the thread that took the action, after the action took effect. An exception it
 * throws is logged and does not undo or fail the action.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface AdminListener {

    /**
     * @param event the action that was taken
     */
    void onAdminAction(AdminEvent event);
}
