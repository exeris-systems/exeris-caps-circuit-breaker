package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerConfig;
import eu.exeris.caps.breaker.api.CircuitBreakerLimitExceededException;
import eu.exeris.caps.breaker.api.CircuitBreakerRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe in-memory registry for {@link CircuitBreaker} instances, bounded by a maximum count.
 *
 * @since 0.1.0
 */
public final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {

    private final ConcurrentHashMap<String, LockFreeCircuitBreaker> registry = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig defaultConfig;
    private final int maxBreakers;
    private final AtomicInteger reserved = new AtomicInteger();
    private final LongAdder limitRejections = new LongAdder();

    public DefaultCircuitBreakerRegistry() {
        this(CircuitBreakerConfig.DEFAULT);
    }

    public DefaultCircuitBreakerRegistry(CircuitBreakerConfig defaultConfig) {
        this(defaultConfig, DEFAULT_MAX_BREAKERS);
    }

    public DefaultCircuitBreakerRegistry(CircuitBreakerConfig defaultConfig, int maxBreakers) {
        this.defaultConfig = Objects.requireNonNull(defaultConfig, "defaultConfig");
        if (maxBreakers < 1) {
            throw new IllegalArgumentException("maxBreakers must be >= 1");
        }
        this.maxBreakers = maxBreakers;
    }

    @Override
    public int maxBreakers() {
        return maxBreakers;
    }

    @Override
    public long limitRejections() {
        return limitRejections.sum();
    }

    @Override
    public CircuitBreaker getOrCreate(String name) {
        return getOrCreate(name, defaultConfig);
    }

    @Override
    public CircuitBreaker getOrCreate(String name, CircuitBreakerConfig config) {
        return getOrCreateInternal(name, config);
    }

    /** The breaker for {@code name}, typed for the admin service in this package. */
    LockFreeCircuitBreaker getOrCreateInternal(String name, CircuitBreakerConfig config) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(config, "config");
        LockFreeCircuitBreaker existing = registry.get(name);
        if (existing != null) {
            return existing;
        }
        // The mapping function runs at most once per absent key; the slot is claimed with a CAS so that
        // concurrent creations of different names can never overshoot the limit.
        return registry.computeIfAbsent(name, k -> {
            if (!reserveSlot()) {
                limitRejections.increment();
                throw new CircuitBreakerLimitExceededException(k, maxBreakers);
            }
            return new LockFreeCircuitBreaker(k, config);
        });
    }

    private boolean reserveSlot() {
        for (;;) {
            int current = reserved.get();
            if (current >= maxBreakers) {
                return false;
            }
            if (reserved.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    @Override
    public Optional<CircuitBreaker> find(String name) {
        return Optional.ofNullable(findInternal(name));
    }

    LockFreeCircuitBreaker findInternal(String name) {
        return name == null ? null : registry.get(name);
    }

    @Override
    public Collection<CircuitBreaker> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Override
    public void close() {
        registry.clear();
        reserved.set(0);
    }
}
