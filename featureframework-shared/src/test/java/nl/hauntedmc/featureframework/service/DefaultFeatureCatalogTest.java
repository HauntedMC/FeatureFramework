package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureDescriptor;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureSnapshot;
import nl.hauntedmc.featureframework.api.feature.FeatureState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFeatureCatalogTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void catalogProjectsSortedPointInTimeLifecycleSnapshots() {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
        FeatureDescriptor vanish = descriptor("vanish");
        FeatureDescriptor queue = descriptor("queue");
        catalog.register(vanish);
        catalog.register(queue);

        assertEquals(FeatureState.DISABLED,
                catalog.find(FeatureId.of("queue")).orElseThrow().state());
        catalog.transition(FeatureId.of("queue"), FeatureState.STARTING);
        catalog.transition(FeatureId.of("queue"), FeatureState.ACTIVE);

        var snapshots = catalog.snapshot();
        assertEquals(2, snapshots.size());
        assertEquals(FeatureId.of("queue"), snapshots.get(0).descriptor().id());
        assertEquals(FeatureState.ACTIVE, snapshots.get(0).state());
        assertEquals(NOW, snapshots.get(0).observedAt());
        assertEquals(NOW, snapshots.get(1).observedAt());
        assertTrue(catalog.find(FeatureId.of("missing")).isEmpty());
    }

    @Test
    void failuresExposeAUsefulMessageAndUnknownTransitionsFailFast() {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
        FeatureId queue = FeatureId.of("queue");
        catalog.register(descriptor("queue"));

        catalog.fail(queue, new IllegalStateException("startup failed"));
        assertEquals(Optional.of("startup failed"), catalog.find(queue).orElseThrow().failure());

        catalog.register(descriptor("queue"));
        catalog.fail(queue, new IllegalArgumentException());
        assertEquals(Optional.of("IllegalArgumentException"),
                catalog.find(queue).orElseThrow().failure());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.transition(FeatureId.of("missing"), FeatureState.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.setConfiguredEnabled(FeatureId.of("missing"), true));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.setUnavailableDependencies(FeatureId.of("missing"), Set.of()));
        assertThrows(NullPointerException.class, () -> catalog.register(null));
        assertThrows(NullPointerException.class, () -> catalog.fail(queue, null));
    }

    @Test
    void longFailureMessagesAreTruncatedToThePublicFailureLimit() {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
        FeatureId queue = FeatureId.of("queue");
        catalog.register(descriptor("queue"));

        catalog.fail(queue, new IllegalStateException("x".repeat(200)));

        FeatureSnapshot snapshot = catalog.find(queue).orElseThrow();
        assertEquals(FeatureState.FAILED, snapshot.state());
        assertEquals(160, snapshot.failure().orElseThrow().length());
        assertEquals(snapshot.failure(), snapshot.failureDetail().orElseThrow().message());
    }

    @Test
    void configurationAvailabilityAndFailurePhaseRemainAuthoritative() {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
        FeatureId queue = FeatureId.of("queue");
        FeatureId friends = FeatureId.of("friends");
        catalog.register(descriptor("queue"));

        catalog.setConfiguredEnabled(queue, true);
        catalog.setUnavailableDependencies(queue, Set.of(friends));
        catalog.fail(queue, "startup", new IllegalStateException("missing provider"));

        FeatureSnapshot snapshot = catalog.find(queue).orElseThrow();
        assertTrue(snapshot.configuredEnabled());
        assertEquals(Set.of(friends), snapshot.unavailableDependencies());
        assertEquals("startup", snapshot.failureDetail().orElseThrow().phase());
        assertTrue(snapshot.generation() >= 3);
    }

    @Test
    void unchangedCatalogProjectionDoesNotAdvanceGenerationOrNotifyListeners() throws Exception {
        DefaultFeatureCatalog catalog = new DefaultFeatureCatalog(Clock.fixed(NOW, ZoneOffset.UTC));
        FeatureId queue = FeatureId.of("queue");
        catalog.register(descriptor("queue"));
        long initialGeneration = catalog.find(queue).orElseThrow().generation();
        java.util.concurrent.atomic.AtomicInteger notifications = new java.util.concurrent.atomic.AtomicInteger();
        AutoCloseable subscription = catalog.subscribe(snapshot -> notifications.incrementAndGet());

        catalog.setConfiguredEnabled(queue, false);
        catalog.setUnavailableDependencies(queue, Set.of());

        assertEquals(initialGeneration, catalog.find(queue).orElseThrow().generation());
        assertEquals(0, notifications.get());
        subscription.close();
    }

    private static FeatureDescriptor descriptor(String id) {
        return new FeatureDescriptor(
                FeatureId.of(id),
                id,
                "1.0.0",
                FeatureClassification.INTERNAL,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }
}
