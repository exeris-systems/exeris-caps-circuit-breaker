package eu.exeris.caps.breaker.api;

/**
 * State machine protecting downstream callers and upstream services against cascading failures.
 *
 * <p>This is the call-site interface: it admits calls and records their outcomes, and nothing here changes a
 * breaker's state by hand. Manual control is {@link CircuitBreakerAdmin}. No operation takes a lock.
 *
 * <p>A call is admitted with {@link #acquirePermission()}, which returns a primitive permit, and its outcome
 * is reported with the permit it was admitted under; {@link Permits} reads it. An outcome whose permit
 * belongs to an earlier state generation is counted in {@link #metrics()} only and never drives a transition.
 * Admitting a call and recording an outcome allocate nothing while the breaker stays in the same state.
 *
 * @since 0.1.0
 */
public interface CircuitBreaker {

    /**
     * @return the unique name or route identifier for this circuit breaker
     */
    String name();

    /**
     * @return the current lifecycle state
     */
    CircuitState state();

    /**
     * @return the current state generation; every state transition increments it
     */
    long generation();

    /**
     * @return the circuit breaker configuration
     */
    CircuitBreakerConfig config();

    /**
     * @return a reading of the breaker's counters
     */
    CircuitBreakerMetrics metrics();

    /**
     * Asks to execute a request against the upstream.
     *
     * <p>Executes on the hot request path and returns a primitive, so it allocates nothing. If the circuit is
     * {@link CircuitState#OPEN} or {@link CircuitState#FORCED_OPEN}, the returned permit is not
     * {@linkplain Permits#permitted(long) permitted}. In {@link CircuitState#HALF_OPEN} at most the configured
     * number of probe permits is issued per generation. A transition that this call performs (the end of the
     * wait period of an OPEN breaker) allocates the state of the new generation.
     *
     * @return the decision, bound to the current generation; read it with {@link Permits}
     */
    long acquirePermission();

    /**
     * Records that the call admitted under {@code permit} succeeded.
     *
     * @param permit        the permit the call was admitted under
     * @param durationNanos the call execution duration in nanoseconds
     */
    void onSuccess(long permit, long durationNanos);

    /**
     * Records that the call admitted under {@code permit} failed, without consulting the classifier.
     *
     * @param permit        the permit the call was admitted under
     * @param durationNanos the call execution duration in nanoseconds
     */
    void onError(long permit, long durationNanos);

    /**
     * Records the outcome of the call admitted under {@code permit}, classified by the breaker's
     * {@link CircuitBreakerConfig#failureClassifier()}.
     *
     * @param permit        the permit the call was admitted under
     * @param outcome       what the call produced
     * @param durationNanos the call execution duration in nanoseconds
     */
    void onOutcome(long permit, CallOutcome outcome, long durationNanos);
}
