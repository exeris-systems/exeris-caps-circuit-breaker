package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CallOutcome;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitState;
import eu.exeris.caps.breaker.api.Permits;
import eu.exeris.kernel.tck.contract.AbstractSubsystemZeroAllocTck;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, with the kernel allocation TCK (JFR events for {@code eu.exeris.*} objects plus the exact per-thread
 * allocated-bytes delta), that the steady-state CLOSED path allocates nothing: {@code acquirePermission} and
 * each of {@code onSuccess}, {@code onError} and {@code onOutcome}, with outcomes that stay below the trip
 * threshold. A transition allocates the state of the new generation and is outside the measured loop.
 */
@DisplayName("LockFreeCircuitBreaker: CLOSED path zero-alloc TCK")
class LockFreeCircuitBreakerAllocationTest extends AbstractSubsystemZeroAllocTck {

    private static final CallOutcome OK = CallOutcome.response(200);
    private static final CallOutcome UNAVAILABLE = CallOutcome.response(503);

    private LockFreeCircuitBreaker breaker;
    private int round;

    @Override
    protected String subsystemName() {
        return "CircuitBreaker";
    }

    @Override
    protected String hotPathDescription() {
        return "acquirePermission + onSuccess/onError/onOutcome in CLOSED";
    }

    @Override
    protected boolean supportsZeroGcHotPath() {
        return true;
    }

    @Override
    protected void bootstrapSubsystem() {
        // Threshold 100%: the window mixes successes and failures, so no failure rate reaches it.
        breaker = new LockFreeCircuitBreaker("alloc", CircuitBreakerConfig.builder()
                .slidingWindowSize(64)
                .minimumNumberOfCalls(64)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenStateNanos(TimeUnit.SECONDS.toNanos(60))
                .build());
    }

    @Override
    protected void runSingleIteration() {
        long permit = breaker.acquirePermission();
        if (!Permits.permitted(permit)) {
            throw new AssertionError("CLOSED must admit");
        }
        switch (round++ & 3) {
            case 0 -> breaker.onSuccess(permit, 1L);
            case 1 -> breaker.onError(permit, 1L);
            case 2 -> breaker.onOutcome(permit, OK, 1L);
            default -> breaker.onOutcome(permit, UNAVAILABLE, 1L);
        }
    }

    @Override
    protected void tearDownSubsystem() {
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.metrics().staleOutcomes()).isZero();
    }
}
