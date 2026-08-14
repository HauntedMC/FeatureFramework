package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCapabilityRegistryTest {

    @Test
    void publishesReplacesAndWithdrawsGenerationSafeCapabilities() {
        DefaultCapabilityRegistry registry = registry();
        FeatureId owner = FeatureId.of("demo");
        TestCapability first = () -> "first";
        TestCapability replacement = () -> "replacement";

        Registration stale = registry.register(owner, TestCapability.class, first);
        var reference = registry.reference(TestCapability.class);
        assertEquals("first", reference.require().value());
        long firstGeneration = reference.generation().orElseThrow();

        Registration active = registry.replace(owner, TestCapability.class, replacement);
        stale.close();
        assertEquals("replacement", reference.require().value());
        assertEquals(firstGeneration + 1, reference.generation().orElseThrow());

        active.close();
        assertFalse(reference.isAvailable());
    }

    @Test
    void rejectsForeignAndConcreteContracts() {
        DefaultCapabilityRegistry registry = registry();
        FeatureId owner = FeatureId.of("demo");

        assertThrows(IllegalArgumentException.class, () -> registry.register(owner, Runnable.class, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> registry.register(owner, String.class, "value"));
    }

    private static DefaultCapabilityRegistry registry() {
        return new DefaultCapabilityRegistry(
                "nl.hauntedmc.featureframework.service",
                DefaultCapabilityRegistryTest.class.getClassLoader()
        );
    }

    private interface TestCapability {
        String value();
    }
}
