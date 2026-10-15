package eu.exeris.caps.breaker.api;

/**
 * A point-in-time reading of a breaker's counters.
 *
 * @param permitted      calls admitted
 * @param rejected       calls short-circuited
 * @param successes      outcomes recorded as success, current generation or not
 * @param failures       outcomes recorded as failure, current generation or not
 * @param staleOutcomes  outcomes that did not drive the state machine: their permit belonged to an earlier
 *                       generation, had already been completed, or was not issued by this breaker
 * @since 0.1.0
 */
public record CircuitBreakerMetrics(long permitted, long rejected, long successes, long failures,
                                    long staleOutcomes) {
}
