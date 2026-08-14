package nl.hauntedmc.featureframework.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureLifecycleTest {
    @Test
    void cleanupQuiescesFirstAndIsIdempotent() {
        List<String> calls = new ArrayList<>();
        FeatureLifecycle lifecycle = new FeatureLifecycle(
                List.of(() -> calls.add("quiesce")),
                List.of(() -> calls.add("cleanup"))
        );

        lifecycle.cleanup();
        lifecycle.cleanup();

        assertEquals(List.of("quiesce", "cleanup"), calls);
        assertEquals(FeatureResourceState.CLOSED, lifecycle.state());
    }

    @Test
    void attemptsEveryStepAndSuppressesLaterFailures() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        FeatureLifecycle lifecycle = new FeatureLifecycle(
                List.of(() -> { throw first; }),
                List.of(() -> { throw second; })
        );

        Throwable result = assertThrows(IllegalStateException.class, lifecycle::cleanup);

        assertEquals(List.of(second), List.of(result.getSuppressed()));
        assertEquals(FeatureResourceState.CLOSED, lifecycle.state());
    }
}
