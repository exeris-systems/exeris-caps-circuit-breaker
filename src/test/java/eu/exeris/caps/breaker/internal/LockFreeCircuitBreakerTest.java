package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CallOutcome;
import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitState;
import eu.exeris.caps.breaker.api.Permits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LockFreeCircuitBreaker Unit Tests")
class LockFreeCircuitBreakerTest {

    private static CircuitBreakerConfig config(int window, int min, int waitMillis, int probes) {
        return CircuitBreakerConfig.builder()
                .slidingWindowSize(window)
                .minimumNumberOfCalls(min)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenStateNanos(TimeUnit.MILLISECONDS.toNanos(waitMillis))
                .permittedNumberOfCallsInHalfOpenState(probes)
                .build();
    }

    private static void fail(CircuitBreaker breaker) {
        long permit = breaker.acquirePermission();
        assertThat(Permits.permitted(permit)).isTrue();
        breaker.onError(permit, 1000);
    }

    private static void succeed(CircuitBreaker breaker) {
        long permit = breaker.acquirePermission();
        assertThat(Permits.permitted(permit)).isTrue();
        breaker.onSuccess(permit, 1000);
    }

    private static LockFreeCircuitBreaker tripped(int waitMillis, int probes) {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, waitMillis, probes));
        fail(breaker);
        fail(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        return breaker;
    }

    @Test
    @DisplayName("Circuit starts CLOSED and permits calls")
    void startsClosedAndPermitsCalls() {
        CircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(10, 5, 100, 2));

        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
    }

    @Test
    @DisplayName("Trips to OPEN when failure rate threshold is exceeded")
    void tripsToOpenWhenThresholdExceeded() {
        CircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(10, 4, 100, 2));

        succeed(breaker);
        succeed(breaker);
        fail(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);

        fail(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
    }

    @Test
    @DisplayName("Every state transition increments the generation, and a permit carries the one it was issued in")
    void generationIncrementsOnEveryTransition() throws Exception {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, 30, 1));
        long closedPermit = breaker.acquirePermission();
        assertThat(Permits.generation(closedPermit)).isEqualTo(breaker.generation());
        long closed = breaker.generation();

        fail(breaker);
        fail(breaker);
        assertThat(breaker.generation()).isEqualTo(closed + 1);

        Thread.sleep(50);
        long probe = breaker.acquirePermission();
        assertThat(Permits.permitted(probe)).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        assertThat(breaker.generation()).isEqualTo(closed + 2);
        assertThat(Permits.generation(probe)).isEqualTo(closed + 2);

        breaker.onSuccess(probe, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.generation()).isEqualTo(closed + 3);
    }

    @Test
    @DisplayName("Short-circuits during OPEN state and recovers to HALF_OPEN after waitDuration")
    void shortCircuitsAndTransitionsToHalfOpen() throws InterruptedException {
        LockFreeCircuitBreaker breaker = tripped(50, 2);

        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
        Thread.sleep(70);

        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
    }

    @Test
    @DisplayName("In HALF_OPEN, successful probe calls recover circuit to CLOSED")
    void halfOpenRecoversToClosedOnSuccess() throws InterruptedException {
        LockFreeCircuitBreaker breaker = tripped(50, 2);
        Thread.sleep(70);

        long first = breaker.acquirePermission();
        long second = breaker.acquirePermission();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(first, 500);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(second, 500);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
    }

    @Test
    @DisplayName("In HALF_OPEN, single failure trips circuit back to OPEN")
    void halfOpenTripsBackToOpenOnFailure() throws InterruptedException {
        LockFreeCircuitBreaker breaker = tripped(50, 2);
        Thread.sleep(70);

        long probe = breaker.acquirePermission();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onOutcome(probe, CallOutcome.response(503), 500);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
    }

    @Test
    @DisplayName("A call admitted in CLOSED that completes after OPEN -> HALF_OPEN neither closes nor reopens")
    void staleClosedOutcomeDoesNotDriveHalfOpen() throws InterruptedException {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, 40, 2));
        long slowSuccess = breaker.acquirePermission();
        long slowFailure = breaker.acquirePermission();
        fail(breaker);
        fail(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        Thread.sleep(60);
        long probe = breaker.acquirePermission();
        assertThat(Permits.permitted(probe)).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        long generation = breaker.generation();

        breaker.onSuccess(slowSuccess, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        breaker.onError(slowFailure, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        assertThat(breaker.generation()).isEqualTo(generation);
        assertThat(breaker.metrics().staleOutcomes()).isEqualTo(2);
        assertThat(breaker.metrics().successes()).isEqualTo(1);
        assertThat(breaker.metrics().failures()).isEqualTo(3);
    }

    @Test
    @DisplayName("A stale success in HALF_OPEN does not count as a probe")
    void staleSuccessIsNotAProbe() throws InterruptedException {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, 40, 1));
        long slow = breaker.acquirePermission();
        fail(breaker);
        fail(breaker);
        Thread.sleep(60);
        long probe = breaker.acquirePermission();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(slow, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(probe, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    @DisplayName("A stale failure in HALF_OPEN does not reopen, and a probe of an older HALF_OPEN does not count")
    void staleFailureAndOlderHalfOpenProbe() throws InterruptedException {
        LockFreeCircuitBreaker breaker = tripped(30, 1);
        Thread.sleep(50);
        long olderProbe = breaker.acquirePermission();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        // The probe never reports; after a further wait period the breaker re-opens, then half-opens again.
        Thread.sleep(50);
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        Thread.sleep(50);
        long newerProbe = breaker.acquirePermission();
        assertThat(Permits.permitted(newerProbe)).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(olderProbe, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        breaker.onError(olderProbe, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);

        breaker.onSuccess(newerProbe, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    @DisplayName("A permit completes once, a foreign or rejected permit never drives a transition")
    void duplicateForeignAndRejectedPermits() {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, 1000, 1));
        LockFreeCircuitBreaker other = new LockFreeCircuitBreaker("o", config(5, 2, 1000, 1));
        long permit = breaker.acquirePermission();
        long foreign = other.acquirePermission();

        breaker.onError(permit, 1);
        breaker.onError(permit, 1);
        breaker.onError(foreign, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.metrics().failures()).isEqualTo(1);
        assertThat(breaker.metrics().staleOutcomes()).isEqualTo(2);

        breaker.onError(breaker.acquirePermission(), 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        long rejected = breaker.acquirePermission();
        assertThat(Permits.permitted(rejected)).isFalse();
        breaker.onSuccess(rejected, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);

        long staleBefore = breaker.metrics().staleOutcomes();
        breaker.onSuccess(0L, 1);
        breaker.onSuccess(rejected | Long.MIN_VALUE, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.metrics().staleOutcomes()).isEqualTo(staleBefore + 2);
    }

    @Test
    @DisplayName("A permit that is never reported does not hold a slot, and its late outcome is stale")
    void displacedPermitIsStale() {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 5, 1000, 1));
        long leaked = breaker.acquirePermission();
        for (int i = 0; i < LockFreeCircuitBreaker.TRACKED_ADMISSIONS; i++) {
            succeed(breaker);
        }
        long staleBefore = breaker.metrics().staleOutcomes();
        breaker.onError(leaked, 1);
        assertThat(breaker.metrics().staleOutcomes()).isEqualTo(staleBefore + 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        succeed(breaker);
    }

    @Test
    @DisplayName("Outcomes classified by the configured classifier drive the window")
    void classifiedOutcomes() {
        LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b", config(5, 2, 1000, 1));
        breaker.onOutcome(breaker.acquirePermission(), CallOutcome.response(404), 1);
        breaker.onOutcome(breaker.acquirePermission(), CallOutcome.response(429), 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.metrics().failures()).isZero();

        breaker.onOutcome(breaker.acquirePermission(), CallOutcome.timeout(), 1);
        breaker.onOutcome(breaker.acquirePermission(), CallOutcome.response(503), 1);
        breaker.onOutcome(breaker.acquirePermission(), CallOutcome.transportError(), 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    @DisplayName("A NaN failure-rate threshold is rejected")
    void nanThresholdIsRejected() {
        assertThatThrownBy(() -> CircuitBreakerConfig.builder().failureRateThreshold(Float.NaN).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Concurrent callers at the end of the wait period never receive more probe permits than the budget")
    void halfOpenProbeBudgetHoldsUnderConcurrency() throws Exception {
        int permitted = 3;
        for (int round = 0; round < 200; round++) {
            LockFreeCircuitBreaker breaker = new LockFreeCircuitBreaker("b",
                    config(10, 2, 1, permitted));
            fail(breaker);
            fail(breaker);
            Thread.sleep(2);

            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger admitted = new AtomicInteger();
            List<Long> issued = java.util.Collections.synchronizedList(new ArrayList<>());
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Thread w = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    long permit = breaker.acquirePermission();
                    if (Permits.permitted(permit)) {
                        admitted.incrementAndGet();
                        issued.add(permit);
                    }
                });
                workers.add(w);
                w.start();
            }
            start.countDown();
            for (Thread w : workers) {
                w.join();
            }
            assertThat(admitted.get()).isLessThanOrEqualTo(permitted);
            assertThat(issued).extracting(Permits::generation).containsOnly(issued.isEmpty() ? 0L : Permits.generation(issued.get(0)));
        }
    }

    @Test
    @DisplayName("Concurrent probe successes close the breaker exactly once")
    void concurrentProbeSuccessesCloseOnce() throws Exception {
        int probes = 4;
        for (int round = 0; round < 100; round++) {
            LockFreeCircuitBreaker breaker = tripped(1, probes);
            Thread.sleep(3);
            List<Long> permits = new ArrayList<>();
            for (int i = 0; i < probes; i++) {
                permits.add(breaker.acquirePermission());
            }
            long halfOpen = breaker.generation();
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> workers = new ArrayList<>();
            for (long permit : permits) {
                Thread w = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    breaker.onSuccess(permit, 1);
                });
                workers.add(w);
                w.start();
            }
            start.countDown();
            for (Thread w : workers) {
                w.join();
            }
            assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
            assertThat(breaker.generation()).isEqualTo(halfOpen + 1);
        }
    }

    @Test
    @DisplayName("A HALF_OPEN breaker whose probes never report returns to OPEN after the wait period")
    void halfOpenWithoutOutcomesDoesNotStayForever() throws Exception {
        LockFreeCircuitBreaker breaker = tripped(30, 2);
        Thread.sleep(50);
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();

        Thread.sleep(50);
        assertThat(Permits.permitted(breaker.acquirePermission())).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);

        Thread.sleep(50);
        assertThat(Permits.permitted(breaker.acquirePermission())).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
    }
}
