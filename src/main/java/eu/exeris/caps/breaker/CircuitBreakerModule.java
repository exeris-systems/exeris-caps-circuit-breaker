package eu.exeris.caps.breaker;

import eu.exeris.caps.breaker.api.CircuitBreakerAdmin;
import eu.exeris.caps.breaker.api.CircuitBreakerRegistry;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;

/**
 * Capability declaration for {@code exeris-caps-circuit-breaker}.
 *
 * <p>Provides the {@link CircuitBreakerRegistry} call-site contract for gateway policy pipelines and, as a
 * separate service, {@link CircuitBreakerAdmin} for manual control, which a composition grants only to whoever
 * may change breaker state.
 *
 * @since 0.1.0
 */
@CapabilityModule
@Provides(service = CircuitBreakerRegistry.class, version = "0.1.0")
@Provides(service = CircuitBreakerAdmin.class, version = "0.1.0")
public final class CircuitBreakerModule {

    private CircuitBreakerModule() {
        // Declaration carrier only — never constructed.
    }
}
