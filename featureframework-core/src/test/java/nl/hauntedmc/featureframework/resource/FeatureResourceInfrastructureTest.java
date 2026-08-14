package nl.hauntedmc.featureframework.resource;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureResourceInfrastructureTest {

    @Test
    void extensionsAreTypedUniqueAndRequiredExplicitly() {
        FeatureResourceExtensions extensions = new FeatureResourceExtensions();
        ResourceKey<String> key = ResourceKey.of(String.class);

        extensions.register(key, "value");

        assertEquals("value", extensions.require(key));
        assertTrue(extensions.contains(String.class));
        assertThrows(IllegalStateException.class, () -> extensions.register(key, "replacement"));
        assertThrows(IllegalStateException.class,
                () -> extensions.require(ResourceKey.of(Integer.class)));
    }

    @Test
    void arbitraryResourcesQuiesceAndCleanInReverseRegistrationOrder() {
        FeatureResourceOwner owner = new FeatureResourceOwner();
        List<String> calls = new ArrayList<>();
        owner.ownPhased("first", value -> calls.add("quiesce-" + value),
                value -> calls.add("cleanup-" + value));
        owner.ownPhased("second", value -> calls.add("quiesce-" + value),
                value -> calls.add("cleanup-" + value));

        owner.quiesce();
        owner.cleanup();
        owner.cleanup();

        assertEquals(List.of(
                "quiesce-second", "quiesce-first", "cleanup-second", "cleanup-first"), calls);
        assertEquals(FeatureResourceState.CLOSED, owner.state());
    }

    @Test
    void registrationsAfterQuiesceAreImmediatelyReleased() {
        FeatureResourceOwner owner = new FeatureResourceOwner();
        List<String> calls = new ArrayList<>();
        owner.quiesce();

        assertThrows(IllegalStateException.class, () -> owner.ownPhased(
                "late", value -> calls.add("quiesce-" + value),
                value -> calls.add("cleanup-" + value)));

        assertEquals(List.of("quiesce-late", "cleanup-late"), calls);
    }
}
