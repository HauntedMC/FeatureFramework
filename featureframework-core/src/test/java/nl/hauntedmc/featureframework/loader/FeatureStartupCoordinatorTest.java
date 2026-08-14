package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.feature.stateful.SnapshotState;
import nl.hauntedmc.featureframework.feature.stateful.StatefulFeature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FeatureStartupCoordinatorTest {
    @Test
    void startsInOrderAndRestoresReloadState() {
        List<String> events = new ArrayList<>();
        TestFeature feature = new TestFeature(events);

        boolean started = FeatureStartupCoordinator.start(
                new TestState("snapshot"),
                () -> "context",
                context -> feature,
                ignored -> events.add("prepare"),
                ignored -> events.add("initialize"),
                ignored -> events.add("activate"),
                ignored -> events.add("register"),
                () -> events.add("starting"),
                () -> events.add("active"),
                ignored -> events.add("failed"),
                ignored -> events.add("cleanup-feature"),
                ignored -> events.add("cleanup-context"),
                () -> events.add("unregister")
        );

        assertTrue(started);
        assertEquals(List.of("starting", "prepare", "initialize", "restore:snapshot",
                "activate", "register", "active"), events);
    }

    @Test
    void cleansConstructedFeatureAndUnregistersOnFailure() {
        List<String> events = new ArrayList<>();

        boolean started = FeatureStartupCoordinator.start(
                null,
                () -> "context",
                context -> new Object(),
                ignored -> events.add("prepare"),
                ignored -> { throw new IllegalStateException("boom"); },
                ignored -> events.add("activate"),
                ignored -> events.add("register"),
                () -> events.add("starting"),
                () -> events.add("active"),
                ignored -> events.add("failed"),
                ignored -> events.add("cleanup-feature"),
                ignored -> events.add("cleanup-context"),
                () -> events.add("unregister")
        );

        assertFalse(started);
        assertEquals(List.of("starting", "prepare", "failed", "cleanup-feature", "unregister"), events);
    }

    private record TestState(String value) implements SnapshotState { }

    private record TestFeature(List<String> events) implements StatefulFeature<TestState> {
        @Override
        public Optional<TestState> captureReloadState() {
            return Optional.empty();
        }

        @Override
        public void restoreReloadState(TestState state) {
            events.add("restore:" + state.value());
        }
    }
}
