package eu.exeris.caps.breaker.api;

/**
 * Decides whether the outcome of a call counts as a failure of the upstream.
 *
 * <p>The classifier is part of a breaker's {@link CircuitBreakerConfig}, so it is chosen per breaker, which
 * is per route in a composition that names breakers after routes.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface FailureClassifier {

    /**
     * @param outcome what the call produced
     * @return {@code true} if the outcome counts as a failure
     */
    boolean isFailure(CallOutcome outcome);

    /**
     * The default classification. It counts as a failure exactly:
     * <ul>
     *   <li>a transport error ({@link CallOutcome.Kind#TRANSPORT_ERROR});</li>
     *   <li>a timeout ({@link CallOutcome.Kind#TIMEOUT});</li>
     *   <li>an HTTP response with status 500, 502, 503 or 504.</li>
     * </ul>
     * Every other response is a success, including 501 and every 4xx. A 4xx status never counts, 429 included:
     * backpressure from the upstream is the business of retry and rate limiting, not a sign that the upstream is
     * unhealthy.
     */
    static FailureClassifier defaults() {
        return DefaultFailureClassifier.INSTANCE;
    }
}
