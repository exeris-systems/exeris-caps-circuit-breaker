package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitBreakerLimitExceededException;
import eu.exeris.caps.breaker.api.CircuitBreakerRegistry;
import eu.exeris.caps.breaker.api.CircuitBreakers;
import eu.exeris.caps.breaker.api.CircuitState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CircuitBreakerRegistry Unit Tests")
class CircuitBreakerRegistryTest {

    @Test
    @DisplayName("Creates new circuit breaker and caches it for identical name")
    void createsAndCachesBreaker() {
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry();

        CircuitBreaker cb1 = registry.circuitBreaker("upstream-a");
        CircuitBreaker cb2 = registry.circuitBreaker("upstream-a");

        assertThat(cb1).isNotNull();
        assertThat(cb1).isSameAs(cb2);
        assertThat(cb1.name()).isEqualTo("upstream-a");
    }

    @Test
    @DisplayName("Supports custom configuration per circuit breaker")
    void supportsCustomConfig() {
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry();
        CircuitBreakerConfig customConfig = CircuitBreakerConfig.builder()
                .slidingWindowSize(20)
                .failureRateThreshold(25.0f)
                .build();

        CircuitBreaker cb = registry.circuitBreaker("custom-upstream", customConfig);
        assertThat(cb.config().slidingWindowSize()).isEqualTo(20);
        assertThat(cb.config().failureRateThreshold()).isEqualTo(25.0f);
    }

    @Test
    @DisplayName("Finds existing breakers and lists all")
    void findsAndListsBreakers() {
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry();

        assertThat(registry.find("missing")).isEmpty();

        registry.circuitBreaker("b1");
        registry.circuitBreaker("b2");

        assertThat(registry.find("b1")).isPresent();
        assertThat(registry.all()).hasSize(2);
    }

    @Test
    @DisplayName("The default maximum is 1024")
    void defaultLimit() {
        assertThat(CircuitBreakers.newRegistry().maxBreakers()).isEqualTo(1024);
        assertThat(CircuitBreakerRegistry.DEFAULT_MAX_BREAKERS).isEqualTo(1024);
    }

    @Test
    @DisplayName("A breaker beyond the limit is a hard error plus a counter, and nothing is evicted")
    void limitIsHardAndNeverEvicts() {
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry(CircuitBreakerConfig.DEFAULT, 2);
        CircuitBreaker first = registry.getOrCreate("a");
        registry.getOrCreate("b");

        assertThatThrownBy(() -> registry.getOrCreate("c"))
                .isInstanceOfSatisfying(CircuitBreakerLimitExceededException.class, e -> {
                    assertThat(e.breakerName()).isEqualTo("c");
                    assertThat(e.limit()).isEqualTo(2);
                });
        assertThatThrownBy(() -> registry.getOrCreate("d", CircuitBreakerConfig.DEFAULT))
                .isInstanceOf(CircuitBreakerLimitExceededException.class);

        assertThat(registry.limitRejections()).isEqualTo(2);
        assertThat(registry.find("c")).isEmpty();
        assertThat(registry.all()).hasSize(2);
        assertThat(registry.getOrCreate("a")).isSameAs(first);
        assertThat(registry.limitRejections()).isEqualTo(2);
    }

    @Test
    @DisplayName("An open circuit keeps its state while the registry is full")
    void openCircuitSurvivesAFullRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry(CircuitBreakerConfig.DEFAULT, 1);
        CircuitBreaker breaker = registry.getOrCreate("only");
        for (int i = 0; i < 5; i++) {
            breaker.onError(breaker.acquirePermission(), 1);
        }
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);

        assertThatThrownBy(() -> registry.getOrCreate("other")).isInstanceOf(CircuitBreakerLimitExceededException.class);

        assertThat(registry.getOrCreate("only")).isSameAs(breaker);
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    @DisplayName("Concurrent creations never exceed the limit and one name yields one breaker")
    void limitHoldsUnderConcurrency() throws Exception {
        int limit = 10;
        CircuitBreakerRegistry registry = CircuitBreakers.newRegistry(CircuitBreakerConfig.DEFAULT, limit);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            int id = t;
            Thread w = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int n = 0; n < 50; n++) {
                    try {
                        registry.getOrCreate("name-" + ((id * 7 + n) % 40));
                        created.incrementAndGet();
                    } catch (CircuitBreakerLimitExceededException expected) {
                        // beyond the limit
                    }
                }
            });
            workers.add(w);
            w.start();
        }
        start.countDown();
        for (Thread w : workers) {
            w.join();
        }
        assertThat(registry.all()).hasSize(limit);
        assertThat(registry.all()).extracting(CircuitBreaker::name).doesNotHaveDuplicates();
        assertThat(registry.limitRejections()).isPositive();
    }

    @Test
    @DisplayName("A limit below one is rejected")
    void limitMustBePositive() {
        assertThatThrownBy(() -> CircuitBreakers.newRegistry(CircuitBreakerConfig.DEFAULT, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
