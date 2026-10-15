package eu.exeris.caps.breaker.internal;

import eu.exeris.caps.breaker.api.AdminAction;
import eu.exeris.caps.breaker.api.AdminEvent;
import eu.exeris.caps.breaker.api.AdminListener;
import eu.exeris.caps.breaker.api.CircuitBreaker;
import eu.exeris.caps.breaker.api.CircuitBreakerAdmin;
import eu.exeris.caps.breaker.api.CircuitBreakerRegistry;
import eu.exeris.caps.breaker.api.CircuitState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link CircuitBreakerAdmin} over a {@link DefaultCircuitBreakerRegistry}.
 *
 * @since 0.1.0
 */
public final class DefaultCircuitBreakerAdmin implements CircuitBreakerAdmin {

    private static final System.Logger LOG = System.getLogger("eu.exeris.caps.breaker.admin");

    private final DefaultCircuitBreakerRegistry registry;
    private final AdminListener listener;

    public DefaultCircuitBreakerAdmin(CircuitBreakerRegistry registry, AdminListener listener) {
        Objects.requireNonNull(registry, "registry");
        this.listener = Objects.requireNonNull(listener, "listener");
        if (!(registry instanceof DefaultCircuitBreakerRegistry own)) {
            throw new IllegalArgumentException("registry was not created by CircuitBreakers.newRegistry");
        }
        this.registry = own;
    }

    @Override
    public AdminEvent forceOpen(String breakerName, String actor) {
        return act(breakerName, actor, AdminAction.FORCE_OPEN);
    }

    @Override
    public AdminEvent disable(String breakerName, String actor) {
        return act(breakerName, actor, AdminAction.DISABLE);
    }

    @Override
    public AdminEvent release(String breakerName, String actor) {
        return act(breakerName, actor, AdminAction.RELEASE);
    }

    @Override
    public Optional<CircuitState> state(String breakerName) {
        return Optional.ofNullable(registry.findInternal(breakerName)).map(LockFreeCircuitBreaker::state);
    }

    @Override
    public Map<String, CircuitState> states() {
        Map<String, CircuitState> states = new LinkedHashMap<>();
        for (CircuitBreaker breaker : registry.all()) {
            states.put(breaker.name(), breaker.state());
        }
        return Map.copyOf(states);
    }

    private AdminEvent act(String breakerName, String actor, AdminAction action) {
        Objects.requireNonNull(breakerName, "breakerName");
        Objects.requireNonNull(actor, "actor");
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        LockFreeCircuitBreaker breaker = registry.findInternal(breakerName);
        if (breaker == null) {
            throw new NoSuchElementException("no circuit breaker named '" + breakerName + "'");
        }
        CircuitState[] change = switch (action) {
            case FORCE_OPEN -> breaker.forceOpen();
            case DISABLE -> breaker.disable();
            case RELEASE -> breaker.release();
        };
        AdminEvent event = new AdminEvent(breakerName, action, change[0], change[1], Instant.now(), actor);
        LOG.log(System.Logger.Level.INFO, "circuit breaker admin action: breaker={0} action={1} {2} -> {3} actor={4}",
                event.breakerName(), event.action(), event.previousState(), event.newState(), event.actor());
        try {
            listener.onAdminAction(event);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR,
                    "admin listener failed for breaker " + breakerName + " action " + action, e);
        }
        return event;
    }
}
