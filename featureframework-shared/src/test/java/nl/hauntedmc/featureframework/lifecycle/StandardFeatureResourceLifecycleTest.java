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
    void attemptsEveryCleanupStepAndAggregatesFailures() {
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

    private static FeatureLifecycle lifecycle(List<String> calls, boolean noData) {
        return StandardFeatureResourceLifecycle.create(
                () -> calls.add("q-listener"), () -> calls.add("c-listener"),
                () -> calls.add("q-task"), () -> calls.add("c-task"),
                () -> calls.add("q-command"), () -> calls.add("c-command"),
                () -> calls.add("q-service"), () -> calls.add("c-service"),
                noData ? null : () -> calls.add("q-data"),
                noData ? null : () -> calls.add("c-data"),
                () -> calls.add("q-cache"), () -> calls.add("c-cache"),
                List.of(() -> calls.add("pre-listener"))
        );
    }
}
