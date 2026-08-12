package nl.hauntedmc.featureframework.api;

import nl.hauntedmc.featureframework.api.feature.*;
import nl.hauntedmc.featureframework.api.model.ServerId;
import nl.hauntedmc.featureframework.api.service.CapabilityUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FrameworkApiContractsTest {

    @Test
    void validatesIdentifiersAndMetadata() {
        FeatureId id = FeatureId.of(" Queue ");
        FeatureDescriptor descriptor = new FeatureDescriptor(
                id,
                "Queue",
                "1.0.0",
                FeatureClassification.CAPABILITY_PROVIDER,
                Set.of(),
                Set.of("example.QueueApi"),
                Set.of(FeatureRole.CAPABILITY_PROVIDER)
        );

        assertEquals("queue", id.value());
        assertEquals("Queue", descriptor.displayName());
        assertThrows(IllegalArgumentException.class, () -> FeatureId.of("bad id"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureDescriptor(
                id, " ", "1", FeatureClassification.INTERNAL, Set.of(), Set.of(), Set.of()
        ));
    }

    @Test
    void providesBothHumanAndTypedFailureProjections() {
        FeatureDescriptor descriptor = new FeatureDescriptor(
                FeatureId.of("demo"), "Demo", "1", Set.of(), Set.of(), Set.of()
        );
        FeatureFailure failure = new FeatureFailure("STARTUP", "initialize", Optional.of("boom"));
        FeatureSnapshot snapshot = new FeatureSnapshot(
                descriptor,
                true,
                FeatureState.FAILED,
                Optional.of(failure),
                Set.of(),
                Instant.EPOCH,
                Optional.empty(),
                1,
                Instant.EPOCH
        );

        assertEquals(Optional.of("boom"), snapshot.failure());
        assertEquals(Optional.of(failure), snapshot.failureDetail());
        assertThrows(IllegalArgumentException.class, () -> new FeatureSnapshot(
                descriptor, true, FeatureState.ACTIVE, Optional.empty(), Set.of(),
                Instant.EPOCH, Optional.empty(), -1, Instant.EPOCH
        ));
    }

    @Test
    void validatesServerIdsAndCapabilityFailures() {
        assertEquals("hub", ServerId.of(" HUB ").value());
        CapabilityUnavailableException failure = new CapabilityUnavailableException(Runnable.class);
        assertEquals(Runnable.class, failure.capabilityType());
        assertTrue(failure.getMessage().contains(Runnable.class.getName()));
    }
}
