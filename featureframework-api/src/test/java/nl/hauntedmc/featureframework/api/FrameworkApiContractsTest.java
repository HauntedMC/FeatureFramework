package nl.hauntedmc.featureframework.api;

import nl.hauntedmc.featureframework.api.feature.*;
import nl.hauntedmc.featureframework.api.network.ServerId;
import nl.hauntedmc.featureframework.api.service.CapabilityUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FrameworkApiContractsTest {

    @Test
    void validatesIdentifiersAndMetadata() {
        FeatureId id = FeatureId.of(" Queue ");
        FeatureMetadata metadata = new FeatureMetadata(
                id,
                "Queue",
                "1.0.0",
                Set.of(),
                Set.of("example.QueueApi"),
                Set.of(FeatureRole.CAPABILITY_PROVIDER)
        );

        assertEquals("queue", id.value());
        assertEquals("Queue", metadata.displayName());
        assertTrue(FeatureId.isValid(" Queue "));
        assertFalse(FeatureId.isValid("bad id"));
        assertFalse(FeatureId.isValid(null));
        assertEquals(Optional.of(id), FeatureId.tryParse(" Queue "));
        assertEquals(Optional.empty(), FeatureId.tryParse("bad id"));
        assertThrows(IllegalArgumentException.class, () -> FeatureId.of("bad id"));
        assertThrows(IllegalArgumentException.class, () -> new FeatureMetadata(
                id, " ", "1", Set.of(), Set.of(), Set.of()
        ));
    }

    @Test
    void featureCatalogAcceptsTextIds() {
        FeatureMetadata metadata = new FeatureMetadata(
                FeatureId.of("demo"), "Demo", "1", Set.of(), Set.of(), Set.of()
        );
        FeatureSnapshot snapshot = new FeatureSnapshot(
                metadata,
                true,
                FeatureState.ACTIVE,
                Optional.empty(),
                Set.of(),
                Instant.EPOCH,
                Optional.empty(),
                1,
                Instant.EPOCH
        );
        FeatureCatalog catalog = new FeatureCatalog() {
            @Override
            public Optional<FeatureSnapshot> find(FeatureId id) {
                return id.equals(metadata.id()) ? Optional.of(snapshot) : Optional.empty();
            }

            @Override
            public List<FeatureSnapshot> snapshot() {
                return List.of(snapshot);
            }

            @Override
            public AutoCloseable subscribe(FeatureCatalogListener listener) {
                return () -> { };
            }
        };

        assertEquals(Optional.of(snapshot), catalog.find(" DEMO "));
        assertEquals(Optional.empty(), catalog.find("missing"));
    }

    @Test
    void providesBothHumanAndTypedFailureProjections() {
        FeatureMetadata metadata = new FeatureMetadata(
                FeatureId.of("demo"), "Demo", "1", Set.of(), Set.of(), Set.of()
        );
        FeatureFailure failure = new FeatureFailure("STARTUP", "initialize", Optional.of("boom"));
        FeatureSnapshot snapshot = new FeatureSnapshot(
                metadata,
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
                metadata, true, FeatureState.ACTIVE, Optional.empty(), Set.of(),
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
