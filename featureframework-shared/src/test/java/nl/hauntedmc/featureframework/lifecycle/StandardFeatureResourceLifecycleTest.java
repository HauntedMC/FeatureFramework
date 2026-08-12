package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardFeatureResourceLifecycleTest {
    @Test
    void preservesStandardQuiesceAndCleanupOrdering() {
        List<String> calls = new ArrayList<>();
        FeatureLifecycle lifecycle = lifecycle(calls, false);

        lifecycle.cleanup();

        assertEquals(List.of(
                "q-listener", "q-task", "q-command", "q-service", "q-data", "q-cache",
                "pre-listener", "c-listener", "c-task", "c-command", "c-service", "c-data", "c-cache"
        ), calls);
        assertEquals(FeatureResourceState.CLOSED, lifecycle.state());

        lifecycle.cleanup();
        assertEquals(13, calls.size(), "cleanup must be idempotent once closed");
    }

    @Test
    void legacyFactoryStillAggregatesFailures() {
        List<String> calls = new ArrayList<>();
        RuntimeException first = new RuntimeException("listener failure");
        RuntimeException later = new RuntimeException("task failure");
        FeatureLifecycle lifecycle = StandardFeatureResourceLifecycle.create(
                () -> calls.add("q-listener"),
                () -> { calls.add("c-listener"); throw first; },
                () -> calls.add("q-task"),
                () -> { calls.add("c-task"); throw later; },
                () -> calls.add("q-command"), () -> calls.add("c-command"),
                () -> calls.add("q-service"), () -> calls.add("c-service"),
                null, null,
                () -> calls.add("q-cache"), () -> calls.add("c-cache"),
                List.of(() -> calls.add("pre-listener"))
        );

        RuntimeException thrown = assertThrows(RuntimeException.class, lifecycle::cleanup);

        assertSame(first, thrown);
        assertEquals(List.of(later), List.of(thrown.getSuppressed()));
        assertEquals(FeatureResourceState.CLOSED, lifecycle.state());
        assertEquals(List.of(
                "q-listener", "q-task", "q-command", "q-service", "q-cache",
                "pre-listener", "c-listener", "c-task", "c-command", "c-service", "c-cache"
        ), calls);
    }

    @Test
    void builderRequiresDataCallbacksAsAPair() {
        StandardFeatureResourceLifecycle.Builder builder = StandardFeatureResourceLifecycle.builder()
                .listeners(() -> { }, () -> { })
                .tasks(() -> { }, () -> { })
                .commands(() -> { }, () -> { })
                .services(() -> { }, () -> { })
                .caches(() -> { }, () -> { });

        assertThrows(NullPointerException.class, () -> builder.data(() -> { }, null));
        assertThrows(NullPointerException.class, () -> builder.data(null, () -> { }));
    }

    private static FeatureLifecycle lifecycle(List<String> calls, boolean noData) {
        StandardFeatureResourceLifecycle.Builder builder = StandardFeatureResourceLifecycle.builder()
                .listeners(() -> calls.add("q-listener"), () -> calls.add("c-listener"))
                .tasks(() -> calls.add("q-task"), () -> calls.add("c-task"))
                .commands(() -> calls.add("q-command"), () -> calls.add("c-command"))
                .services(() -> calls.add("q-service"), () -> calls.add("c-service"))
                .caches(() -> calls.add("q-cache"), () -> calls.add("c-cache"))
                .beforeListenerCleanup(() -> calls.add("pre-listener"));
        if (!noData) {
            builder.data(() -> calls.add("q-data"), () -> calls.add("c-data"));
        }
        return builder.build();
    }
}
