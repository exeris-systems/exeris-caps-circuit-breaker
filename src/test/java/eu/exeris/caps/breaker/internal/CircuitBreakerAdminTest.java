package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.AdminAction;
import eu.exeris.caps.breaker.api.AdminEvent;
import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerAdmin;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitBreakerRegistry;
import eu.exeris.caps.breaker.api.CircuitBreakers;
import eu.exeris.caps.breaker.api.CircuitState;
import eu.exeris.caps.breaker.api.Permit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CircuitBreakerAdmin")
class CircuitBreakerAdminTest {

    private final List<AdminEvent> events = new ArrayList<>();
    private final CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .slidingWindowSize(5).minimumNumberOfCalls(2)
            .waitDurationInOpenStateNanos(TimeUnit.MILLISECONDS.toNanos(20))
            .permittedNumberOfCallsInHalfOpenState(1).build();
    private final CircuitBreakerRegistry registry = CircuitBreakers.newRegistry(config);
    private final CircuitBreakerAdmin admin = CircuitBreakers.newAdmin(registry, events::add);

    @Test
    @DisplayName("FORCED_OPEN refuses every call, and neither time nor outcomes leave it")
    void forcedOpenIsHeld() throws Exception {
        CircuitBreaker breaker = registry.getOrCreate("route-a");
        Permit inFlight = breaker.acquirePermission();

        AdminEvent event = admin.forceOpen("route-a", "alice");

        assertThat(event.previousState()).isEqualTo(CircuitState.CLOSED);
        assertThat(event.newState()).isEqualTo(CircuitState.FORCED_OPEN);
        assertThat(breaker.state()).isEqualTo(CircuitState.FORCED_OPEN);
        Thread.sleep(50);
        assertThat(breaker.acquirePermission().permitted()).isFalse();
        breaker.onSuccess(inFlight, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.FORCED_OPEN);
        assertThat(breaker.acquirePermission().permitted()).isFalse();
    }

    @Test
    @DisplayName("DISABLED permits every call and failures never trip it")
    void disabledAlwaysPermits() {
        CircuitBreaker breaker = registry.getOrCreate("route-a");
        admin.disable("route-a", "alice");

        for (int i = 0; i < 20; i++) {
            Permit permit = breaker.acquirePermission();
            assertThat(permit.permitted()).isTrue();
            breaker.onError(permit, 1);
        }
        assertThat(breaker.state()).isEqualTo(CircuitState.DISABLED);
    }

    @Test
    @DisplayName("Release returns to CLOSED with fresh counters from either manual state and from OPEN")
    void releaseReturnsToClosedWithFreshCounters() {
        CircuitBreaker breaker = registry.getOrCreate("route-a");
        breaker.onError(breaker.acquirePermission(), 1);
        admin.forceOpen("route-a", "alice");
        AdminEvent released = admin.release("route-a", "alice");
        assertThat(released.previousState()).isEqualTo(CircuitState.FORCED_OPEN);
        assertThat(released.newState()).isEqualTo(CircuitState.CLOSED);

        // The earlier failure was discarded: one more failure is below minimumNumberOfCalls(2).
        breaker.onError(breaker.acquirePermission(), 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);

        admin.disable("route-a", "alice");
        assertThat(admin.release("route-a", "alice").previousState()).isEqualTo(CircuitState.DISABLED);
        breaker.onError(breaker.acquirePermission(), 1);
        breaker.onError(breaker.acquirePermission(), 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        assertThat(admin.release("route-a", "bob").previousState()).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    @DisplayName("An outcome admitted before an admin action never drives a transition afterwards")
    void adminActionStartsANewGeneration() {
        CircuitBreaker breaker = registry.getOrCreate("route-a");
        Permit a = breaker.acquirePermission();
        Permit b = breaker.acquirePermission();
        long before = breaker.generation();
        admin.release("route-a", "alice");
        assertThat(breaker.generation()).isEqualTo(before + 1);

        breaker.onError(a, 1);
        breaker.onError(b, 1);
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    @DisplayName("Every action is reported with breaker, action, states, timestamp and actor")
    void listenerReceivesEveryAction() {
        registry.getOrCreate("route-a");
        Instant from = Instant.now();

        admin.forceOpen("route-a", "alice");
        admin.release("route-a", "bob");

        assertThat(events).hasSize(2);
        AdminEvent first = events.get(0);
        assertThat(first.breakerName()).isEqualTo("route-a");
        assertThat(first.action()).isEqualTo(AdminAction.FORCE_OPEN);
        assertThat(first.previousState()).isEqualTo(CircuitState.CLOSED);
        assertThat(first.newState()).isEqualTo(CircuitState.FORCED_OPEN);
        assertThat(first.actor()).isEqualTo("alice");
        assertThat(first.timestamp()).isBetween(from, Instant.now());
        assertThat(events.get(1).action()).isEqualTo(AdminAction.RELEASE);
        assertThat(events.get(1).actor()).isEqualTo("bob");
    }

    @Test
    @DisplayName("A failing listener does not undo the action")
    void failingListenerDoesNotUndo() {
        CircuitBreakerAdmin throwing = CircuitBreakers.newAdmin(registry, e -> {
            throw new IllegalStateException("audit sink down");
        });
        CircuitBreaker breaker = registry.getOrCreate("route-a");

        throwing.forceOpen("route-a", "alice");

        assertThat(breaker.state()).isEqualTo(CircuitState.FORCED_OPEN);
    }

    @Test
    @DisplayName("State reads, unknown names and blank actors")
    void readsAndValidation() {
        registry.getOrCreate("route-a");
        admin.disable("route-a", "alice");

        assertThat(admin.state("route-a")).contains(CircuitState.DISABLED);
        assertThat(admin.state("missing")).isEmpty();
        assertThat(admin.states()).containsEntry("route-a", CircuitState.DISABLED);
        assertThatThrownBy(() -> admin.forceOpen("missing", "alice")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> admin.forceOpen("route-a", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admin.forceOpen("route-a", null)).isInstanceOf(NullPointerException.class);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("The call-site interface exposes no state-changing method")
    void callSiteInterfaceHasNoManualControl() {
        assertThat(CircuitBreaker.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("transitionToOpen", "transitionToClosed", "transitionToHalfOpen", "reset",
                        "forceOpen", "disable", "release", "tryAcquirePermission", "recordSuccess",
                        "recordFailure");
    }
}
