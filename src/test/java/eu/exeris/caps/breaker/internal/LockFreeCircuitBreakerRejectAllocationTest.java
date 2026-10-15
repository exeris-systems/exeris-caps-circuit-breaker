package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitState;
import eu.exeris.caps.breaker.api.Permits;
import eu.exeris.kernel.tck.contract.AbstractSubsystemZeroAllocTck;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves with the kernel allocation TCK that short-circuiting a call while the breaker is OPEN allocates
 * nothing, and that reporting an outcome for such a refused permit does not either.
 */
@DisplayName("LockFreeCircuitBreaker: OPEN path zero-alloc TCK")
class LockFreeCircuitBreakerRejectAllocationTest extends AbstractSubsystemZeroAllocTck {

    private LockFreeCircuitBreaker breaker;

    @Override
    protected String subsystemName() {
        return "CircuitBreaker";
    }

    @Override
    protected String hotPathDescription() {
        return "acquirePermission + onSuccess on a refused permit in OPEN";
    }

    @Override
    protected boolean supportsZeroGcHotPath() {
        return true;
    }

    @Override
    protected void bootstrapSubsystem() {
        breaker = new LockFreeCircuitBreaker("alloc-open", CircuitBreakerConfig.builder()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenStateNanos(TimeUnit.SECONDS.toNanos(600))
                .build());
        breaker.onError(breaker.acquirePermission(), 1L);
        breaker.onError(breaker.acquirePermission(), 1L);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
    }

    @Override
    protected void runSingleIteration() {
        long permit = breaker.acquirePermission();
        if (Permits.permitted(permit)) {
            throw new AssertionError("OPEN must refuse");
        }
        breaker.onSuccess(permit, 1L);
    }

    @Override
    protected void tearDownSubsystem() {
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
    }
}
