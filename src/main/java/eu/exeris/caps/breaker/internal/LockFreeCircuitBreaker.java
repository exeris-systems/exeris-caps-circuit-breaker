package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CallOutcome;
import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitBreakerMetrics;
import eu.exeris.caps.breaker.api.CircuitState;
import eu.exeris.caps.breaker.api.Permits;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free implementation of {@link CircuitBreaker}.
 *
 * <p>The state lives in one immutable-identity {@link Phase} per state generation, swapped with a
 * compare-and-set. A phase owns its own counters (the sliding window of a CLOSED phase, the probe counters of
 * a HALF_OPEN phase), so an outcome that arrives after the breaker has moved on can only touch the phase it
 * was admitted in, which no longer drives anything.
 *
 * <p>A permit is a {@code long}: {@code [admitted:1][breaker:20][generation:20][ticket:23]}. Admissions are
 * numbered by a ticket counter and the permit of each admitted call is written into a fixed table, indexed by
 * ticket. Completing an outcome clears the table entry with a compare-and-set on the whole permit, so exactly
 * one outcome per permit gets through, and a permit of another breaker, of an earlier generation or one that
 * was never admitted finds no matching entry or the wrong identity. The table is allocated once with the
 * breaker; an admission overwrites the entry of the admission {@value #TRACKED_ADMISSIONS} tickets before
 * it, so a permit that is never reported cannot hold a slot.
 *
 * <p>The admission and outcome paths allocate nothing while the breaker stays in one state; a transition
 * allocates the {@link Phase} of the new generation.
 *
 * @since 0.1.0
 */
public final class LockFreeCircuitBreaker implements CircuitBreaker {

    /** Admissions remembered for completion tracking; a power of two. */
    static final int TRACKED_ADMISSIONS = CircuitBreakerConfig.MAX_PERMITTED_CALLS_IN_HALF_OPEN_STATE;

    private static final int ADMITTED_SHIFT = 63;
    private static final int BREAKER_SHIFT = 43;
    private static final int GENERATION_SHIFT = 23;
    private static final long BREAKER_MASK = (1L << 20) - 1;
    private static final long GENERATION_MASK = (1L << Permits.GENERATION_BITS) - 1;
    private static final long TICKET_MASK = (1L << 23) - 1;
    private static final AtomicInteger BREAKER_IDS = new AtomicInteger();

    private static final int OUTCOME_EMPTY = 0;
    private static final int OUTCOME_SUCCESS = 1;
    private static final int OUTCOME_FAILURE = 2;

    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<Phase> phase = new AtomicReference<>();
    private final long breakerId = BREAKER_IDS.getAndIncrement() & BREAKER_MASK;
    private final AtomicLong tickets = new AtomicLong();
    private final AtomicLongArray admissions = new AtomicLongArray(TRACKED_ADMISSIONS);

    private final LongAdder permitted = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder staleOutcomes = new LongAdder();

    public LockFreeCircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = Objects.requireNonNull(name, "name");
        this.config = Objects.requireNonNull(config, "config");
        this.phase.set(Phase.closed(0, config.slidingWindowSize()));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CircuitState state() {
        return phase.get().state;
    }

    @Override
    public long generation() {
        return phase.get().generation;
    }

    @Override
    public CircuitBreakerConfig config() {
        return config;
    }

    @Override
    public CircuitBreakerMetrics metrics() {
        return new CircuitBreakerMetrics(permitted.sum(), rejected.sum(), successes.sum(), failures.sum(),
                staleOutcomes.sum());
    }

    // ---- admission --------------------------------------------------------------------------------------

    @Override
    public long acquirePermission() {
        for (;;) {
            Phase p = phase.get();
            switch (p.state) {
                case CLOSED, DISABLED -> {
                    return admit(p);
                }
                case FORCED_OPEN -> {
                    return reject(p);
                }
                case OPEN -> {
                    // Overflow-safe comparison of two System.nanoTime() readings.
                    if (System.nanoTime() - p.deadlineNanos < 0) {
                        return reject(p);
                    }
                    Phase half = Phase.halfOpen(p.generation + 1,
                            System.nanoTime() + config.waitDurationInOpenStateNanos());
                    phase.compareAndSet(p, half);
                    // Re-read: either this call moved the breaker to HALF_OPEN or another one did.
                }
                case HALF_OPEN -> {
                    int budget = config.permittedNumberOfCallsInHalfOpenState();
                    int calls = p.probesIssued.get();
                    if (calls < budget) {
                        if (p.probesIssued.compareAndSet(calls, calls + 1)) {
                            return admit(p);
                        }
                    } else {
                        // Budget spent. If the probes never reported back within a wait period, re-open so
                        // the breaker is not stuck in HALF_OPEN.
                        if (System.nanoTime() - p.deadlineNanos >= 0) {
                            tripFrom(p);
                        }
                        return reject(p);
                    }
                }
            }
        }
    }

    private long admit(Phase p) {
        permitted.increment();
        long ticket = tickets.getAndIncrement() & TICKET_MASK;
        long permit = encode(true, p.generation, ticket);
        admissions.set((int) (ticket & (TRACKED_ADMISSIONS - 1)), permit);
        return permit;
    }

    private long reject(Phase p) {
        rejected.increment();
        return encode(false, p.generation, 0L);
    }

    private long encode(boolean admitted, long generation, long ticket) {
        return (admitted ? 1L << ADMITTED_SHIFT : 0L) | (breakerId << BREAKER_SHIFT)
                | ((generation & GENERATION_MASK) << GENERATION_SHIFT) | ticket;
    }

    /** @return {@code true} for the first completion of an admission of this breaker only */
    private boolean complete(long permit) {
        if (!Permits.permitted(permit) || ((permit >>> BREAKER_SHIFT) & BREAKER_MASK) != breakerId) {
            return false;
        }
        return admissions.compareAndSet((int) (permit & (TRACKED_ADMISSIONS - 1)), permit, 0L);
    }

    // ---- outcomes ---------------------------------------------------------------------------------------

    @Override
    public void onSuccess(long permit, long durationNanos) {
        record(permit, false);
    }

    @Override
    public void onError(long permit, long durationNanos) {
        record(permit, true);
    }

    @Override
    public void onOutcome(long permit, CallOutcome outcome, long durationNanos) {
        Objects.requireNonNull(outcome, "outcome");
        record(permit, config.failureClassifier().isFailure(outcome));
    }

    private void record(long permit, boolean failure) {
        if (!complete(permit)) {
            // Foreign, rejected or already completed: counted, never acted on.
            staleOutcomes.increment();
            return;
        }
        (failure ? failures : successes).increment();

        Phase p = phase.get();
        if (Permits.generation(permit) != (p.generation & GENERATION_MASK)) {
            staleOutcomes.increment();
            return;
        }
        switch (p.state) {
            case CLOSED -> recordClosed(p, failure);
            case HALF_OPEN -> {
                if (failure) {
                    // Any failure of a probe of this generation trips back to OPEN.
                    tripFrom(p);
                } else if (p.probesSucceeded.incrementAndGet() >= config.permittedNumberOfCallsInHalfOpenState()) {
                    phase.compareAndSet(p, Phase.closed(p.generation + 1, config.slidingWindowSize()));
                }
            }
            default -> {
                // OPEN, FORCED_OPEN, DISABLED: nothing in flight can drive a transition.
            }
        }
    }

    private void recordClosed(Phase p, boolean failure) {
        long idx = p.head.getAndIncrement();
        int slot = (int) Math.floorMod(idx, p.ring.length());
        p.ring.set(slot, failure ? OUTCOME_FAILURE : OUTCOME_SUCCESS);
        if (!failure) {
            return;
        }
        int total = 0;
        int fails = 0;
        for (int i = 0; i < p.ring.length(); i++) {
            int val = p.ring.get(i);
            if (val != OUTCOME_EMPTY) {
                total++;
                if (val == OUTCOME_FAILURE) {
                    fails++;
                }
            }
        }
        if (total >= config.minimumNumberOfCalls() && (fails * 100.0f) / total >= config.failureRateThreshold()) {
            tripFrom(p);
        }
    }

    /** Moves from the given phase to OPEN, and only if the breaker is still in exactly that phase. */
    private void tripFrom(Phase expected) {
        phase.compareAndSet(expected,
                Phase.open(expected.generation + 1, System.nanoTime() + config.waitDurationInOpenStateNanos()));
    }

    // ---- manual control, reached only through DefaultCircuitBreakerAdmin ---------------------------------

    /** Holds the breaker open. @return {previous, new} state */
    CircuitState[] forceOpen() {
        return swap(g -> Phase.forced(g, CircuitState.FORCED_OPEN));
    }

    /** Bypasses the breaker. @return {previous, new} state */
    CircuitState[] disable() {
        return swap(g -> Phase.forced(g, CircuitState.DISABLED));
    }

    /** Returns the breaker to CLOSED with fresh counters. @return {previous, new} state */
    CircuitState[] release() {
        return swap(g -> Phase.closed(g, config.slidingWindowSize()));
    }

    private CircuitState[] swap(java.util.function.LongFunction<Phase> next) {
        for (;;) {
            Phase prev = phase.get();
            Phase fresh = next.apply(prev.generation + 1);
            if (phase.compareAndSet(prev, fresh)) {
                return new CircuitState[] {prev.state, fresh.state};
            }
        }
    }

    @Override
    public String toString() {
        return "CircuitBreaker[" + name + ", state=" + state() + "]";
    }

    // ---- state ------------------------------------------------------------------------------------------

    /** One state generation with the counters that belong to it. */
    private static final class Phase {
        final CircuitState state;
        final long generation;
        /** OPEN: end of the wait period. HALF_OPEN: end of the probe period. */
        final long deadlineNanos;
        final AtomicIntegerArray ring;
        final AtomicLong head = new AtomicLong();
        final AtomicInteger probesIssued = new AtomicInteger();
        final AtomicInteger probesSucceeded = new AtomicInteger();

        private Phase(CircuitState state, long generation, long deadlineNanos, int window) {
            this.state = state;
            this.generation = generation;
            this.deadlineNanos = deadlineNanos;
            this.ring = new AtomicIntegerArray(window);
        }

        static Phase closed(long generation, int window) {
            return new Phase(CircuitState.CLOSED, generation, 0L, window);
        }

        static Phase open(long generation, long untilNanos) {
            return new Phase(CircuitState.OPEN, generation, untilNanos, 0);
        }

        static Phase halfOpen(long generation, long deadlineNanos) {
            return new Phase(CircuitState.HALF_OPEN, generation, deadlineNanos, 0);
        }

        static Phase forced(long generation, CircuitState state) {
            return new Phase(state, generation, 0L, 0);
        }
    }
}
