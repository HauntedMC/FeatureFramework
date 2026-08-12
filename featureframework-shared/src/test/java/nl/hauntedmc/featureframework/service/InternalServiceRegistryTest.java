package nl.hauntedmc.featureframework.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InternalServiceRegistryTest {
    private interface Greeting { String text(); }

    @Test
    void registrationsAreOwnedReplaceableAndIdempotentlyClosed() {
        InternalServiceRegistry<String> registry = new InternalServiceRegistry<>();
        Registration first = registry.register("alpha", Greeting.class, () -> "one");

        assertEquals("one", registry.require(Greeting.class).text());
        assertEquals("alpha", registry.owner(Greeting.class).orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> registry.replace("beta", Greeting.class, () -> "other"));

        Registration second = registry.replace("alpha", Greeting.class, () -> "two");
        first.close();
        assertEquals("two", registry.require(Greeting.class).text());
        second.close();
        second.close();
        assertTrue(registry.find(Greeting.class).isEmpty());
    }

    @Test
    void rejectsConcreteContractsAndDuplicateProviders() {
        InternalServiceRegistry<String> registry = new InternalServiceRegistry<>();
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("owner", String.class, "value"));
        registry.register("owner", Greeting.class, () -> "one");
        assertThrows(IllegalStateException.class,
                () -> registry.register("owner", Greeting.class, () -> "two"));
    }
}
