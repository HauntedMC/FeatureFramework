package nl.hauntedmc.featureframework.runtime;

import nl.hauntedmc.featureframework.api.RuntimeState;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FeatureRuntimeTest {
    @Test
    void ownsStableRegistriesAndReadinessTransitions() {
        CapabilityRegistry capabilities = mock(CapabilityRegistry.class);
        FeatureRuntime<String, CapabilityRegistry> runtime = new FeatureRuntime<>("Demo", capabilities);

        runtime.markReloading();
        assertEquals(RuntimeState.RELOADING, runtime.state());
        runtime.markDegraded();
        assertEquals(RuntimeState.DEGRADED, runtime.state());
        runtime.markReady();

        assertEquals(RuntimeState.READY, runtime.state());
        assertSame(capabilities, runtime.capabilities());
        runtime.whenReady().toCompletableFuture().join();
    }

    @Test
    void failureAndEarlyStopCompleteReadinessExceptionally() {
        FeatureRuntime<String, CapabilityRegistry> failed =
                new FeatureRuntime<>("Failed", mock(CapabilityRegistry.class));
        IllegalStateException cause = new IllegalStateException("boom");
        failed.markDegraded(cause);

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> failed.whenReady().toCompletableFuture().join()
        );
        assertSame(cause, failure.getCause());

        FeatureRuntime<String, CapabilityRegistry> stopped =
                new FeatureRuntime<>("Stopped", mock(CapabilityRegistry.class));
        stopped.markStopping();
        stopped.markStopped(null);
        assertEquals(RuntimeState.STOPPED, stopped.state());
        assertThrows(CompletionException.class, () -> stopped.whenReady().toCompletableFuture().join());
    }
}
