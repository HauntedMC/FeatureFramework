package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FeatureServiceManagerTest {

    @Test
    void stagesActivatesReplacesAndCleansUpOwnedServices() {
        InternalServiceRegistry<String> publicRegistry = new InternalServiceRegistry<>();
        InternalServiceRegistry<String> internalRegistry = new InternalServiceRegistry<>();
        FeatureServiceManager<String> manager = new FeatureServiceManager<>();
        AtomicInteger activations = new AtomicInteger();
        Service first = () -> "first";
        Service replacement = () -> "replacement";

        manager.bindRegistries(publicRegistry, internalRegistry, "demo");
        manager.registerActivationHook(activations::incrementAndGet);
        manager.registerService(Service.class, first);
        manager.registerInternalService(InternalPort.class, () -> "internal");
        assertFalse(publicRegistry.find(Service.class).isPresent());

        manager.activateServices();
        assertTrue(manager.isActive());
        assertEquals(1, activations.get());
        assertSame(first, publicRegistry.require(Service.class));
        assertEquals("internal", internalRegistry.require(InternalPort.class).value());

        manager.registerService(Service.class, replacement);
        assertSame(replacement, publicRegistry.require(Service.class));

        manager.unregisterAllServices();
        assertEquals(FeatureResourceState.CLOSED, manager.state());
        assertTrue(publicRegistry.find(Service.class).isEmpty());
        assertTrue(internalRegistry.find(InternalPort.class).isEmpty());
    }

    @Test
    void requiresBindingAndRejectsRebindingAfterRegistration() {
        FeatureServiceManager<String> manager = new FeatureServiceManager<>();
        assertThrows(IllegalStateException.class, () -> manager.registerService(Service.class, () -> "value"));

        InternalServiceRegistry<String> first = new InternalServiceRegistry<>();
        manager.bindRegistries(first, new InternalServiceRegistry<>(), "demo");
        manager.registerService(Service.class, () -> "value");
        assertThrows(
                IllegalStateException.class,
                () -> manager.bindRegistries(new InternalServiceRegistry<>(), new InternalServiceRegistry<>(), "other")
        );
    }

    private interface Service {
        String value();
    }

    private interface InternalPort {
        String value();
    }
}
