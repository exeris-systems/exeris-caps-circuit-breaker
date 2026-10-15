package eu.exeris.caps.breaker.api;

import java.time.Duration;

/**
 * Immutable configuration for a {@link CircuitBreaker}.
 *
 * <p>{@code failureClassifier} decides which outcomes count as failures; the default is
 * {@link FailureClassifier#defaults()}.
 *
 * @since 0.1.0
 */
public record CircuitBreakerConfig(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        long waitDurationInOpenStateNanos,
        int permittedNumberOfCallsInHalfOpenState,
        FailureClassifier failureClassifier
) {

    /**
     * The most probe calls a HALF_OPEN generation may admit: the number of admissions a breaker keeps track of,
     * so that every probe can still report its outcome.
     */
    public static final int MAX_PERMITTED_CALLS_IN_HALF_OPEN_STATE = 1024;

    public static final CircuitBreakerConfig DEFAULT = new CircuitBreakerConfig(
            20, 5, 50.0f, 3_000_000_000L, 5, FailureClassifier.defaults()
    );

    public static CircuitBreakerConfig ofDefaults() {
        return DEFAULT;
    }

    public CircuitBreakerConfig {
        java.util.Objects.requireNonNull(failureClassifier, "failureClassifier");
        if (slidingWindowSize <= 0) {
            throw new IllegalArgumentException("slidingWindowSize must be > 0");
        }
        if (minimumNumberOfCalls <= 0 || minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalArgumentException("minimumNumberOfCalls must be > 0 and <= slidingWindowSize");
        }
        if (Float.isNaN(failureRateThreshold) || failureRateThreshold <= 0.0f || failureRateThreshold > 100.0f) {
            throw new IllegalArgumentException("failureRateThreshold must be between 0 and 100");
        }
        if (waitDurationInOpenStateNanos <= 0) {
            throw new IllegalArgumentException("waitDurationInOpenStateNanos must be > 0");
        }
        if (permittedNumberOfCallsInHalfOpenState <= 0) {
            throw new IllegalArgumentException("permittedNumberOfCallsInHalfOpenState must be > 0");
        }
        if (permittedNumberOfCallsInHalfOpenState > MAX_PERMITTED_CALLS_IN_HALF_OPEN_STATE) {
            throw new IllegalArgumentException(
                    "permittedNumberOfCallsInHalfOpenState must be <= " + MAX_PERMITTED_CALLS_IN_HALF_OPEN_STATE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 5;
        private float failureRateThreshold = 50.0f;
        private long waitDurationInOpenStateNanos = 3_000_000_000L;
        private int permittedNumberOfCallsInHalfOpenState = 5;
        private FailureClassifier failureClassifier = FailureClassifier.defaults();

        public Builder slidingWindowSize(int size) {
            this.slidingWindowSize = size;
            return this;
        }

        public Builder minimumNumberOfCalls(int calls) {
            this.minimumNumberOfCalls = calls;
            return this;
        }

        public Builder failureRateThreshold(float percentage) {
            this.failureRateThreshold = percentage;
            return this;
        }

        public Builder waitDurationInOpenState(Duration duration) {
            this.waitDurationInOpenStateNanos = duration.toNanos();
            return this;
        }

        public Builder waitDurationInOpenStateNanos(long nanos) {
            this.waitDurationInOpenStateNanos = nanos;
            return this;
        }

        public Builder permittedNumberOfCallsInHalfOpenState(int calls) {
            this.permittedNumberOfCallsInHalfOpenState = calls;
            return this;
        }

        public Builder failureClassifier(FailureClassifier classifier) {
            this.failureClassifier = classifier;
            return this;
        }

        public CircuitBreakerConfig build() {
            return new CircuitBreakerConfig(
                    slidingWindowSize,
                    minimumNumberOfCalls,
                    failureRateThreshold,
                    waitDurationInOpenStateNanos,
                    permittedNumberOfCallsInHalfOpenState,
                    failureClassifier
            );
        }
    }
}
