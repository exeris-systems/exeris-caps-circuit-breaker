package eu.exeris.caps.breaker;

import eu.exeris.kernel.spi.contract.EntitlementRequirement;
import eu.exeris.kernel.tck.contract.AbstractEntitlementRequirementTck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("exeris-caps-circuit-breaker: EntitlementRequirement")
class CircuitBreakerEntitlementTest extends AbstractEntitlementRequirementTck {

    @Override
    protected Class<? extends EntitlementRequirement> providerClass() {
        return CircuitBreakerEntitlement.class;
    }

    @Test
    @DisplayName("ServiceLoader discovers the provider from the cap's own service registration")
    void serviceLoaderDiscoversTheProvider() {
        List<Class<? extends EntitlementRequirement>> types = ServiceLoader.load(EntitlementRequirement.class)
                .stream()
                .map(ServiceLoader.Provider::type)
                .toList();

        assertThat(types).contains(CircuitBreakerEntitlement.class);
    }

    @Test
    @DisplayName("It requires exactly this cap's own capability identifier")
    void requiresOnlyItsOwnCapability() {
        assertThat(new CircuitBreakerEntitlement().requiredCapabilities()).containsExactly("exeris-caps-circuit-breaker");
        assertThat(CircuitBreakerEntitlement.CAPABILITY_ID).isEqualTo("exeris-caps-circuit-breaker");
    }
}
