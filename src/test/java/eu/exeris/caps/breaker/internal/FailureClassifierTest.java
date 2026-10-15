package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CallOutcome;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.FailureClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FailureClassifier default table")
class FailureClassifierTest {

    private final FailureClassifier defaults = FailureClassifier.defaults();

    @Test
    @DisplayName("Transport errors and timeouts count")
    void transportErrorsAndTimeoutsCount() {
        assertThat(defaults.isFailure(CallOutcome.transportError())).isTrue();
        assertThat(defaults.isFailure(CallOutcome.timeout())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    @DisplayName("HTTP 500, 502, 503 and 504 count")
    void listedServerErrorsCount(int status) {
        assertThat(defaults.isFailure(CallOutcome.response(status))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 301, 304, 400, 401, 403, 404, 408, 409, 422, 429, 451, 499, 501, 505, 599})
    @DisplayName("Every other status does not count, 4xx and 429 included")
    void otherStatusesDoNotCount(int status) {
        assertThat(defaults.isFailure(CallOutcome.response(status))).isFalse();
    }

    @Test
    @DisplayName("The default config uses the default classifier, and a breaker can be given its own")
    void configurable() {
        assertThat(CircuitBreakerConfig.DEFAULT.failureClassifier()).isSameAs(defaults);
        FailureClassifier strict = outcome -> outcome.statusCode() >= 400;
        assertThat(CircuitBreakerConfig.builder().failureClassifier(strict).build().failureClassifier())
                .isSameAs(strict);
    }
}
