package nl.hauntedmc.featureframework.operation.reload;

import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResult;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureGraphReloaderTest {
    @Test
    void reconcilesDisabledReloadedAndNewlyEnabledFeatures() {
        Set<String> loaded = new LinkedHashSet<>(List.of("disabled", "active"));
        Set<String> reloaded = new LinkedHashSet<>();

        FeatureGraphReloadResult result = FeatureGraphReloader.reload(
                () -> { },
                () -> { },
                () -> loaded,
                loaded::contains,
                feature -> !feature.equals("disabled"),
                feature -> {
                    loaded.remove(feature);
                    return new FeatureDisableResponse(FeatureDisableResult.SUCCESS, feature, Set.of());
                },
                feature -> {
                    reloaded.add(feature);
                    return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, feature, Set.of());
                },
                () -> List.of("disabled", "active", "new"),
                feature -> {
                    loaded.add(feature);
                    return new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
                }
        );

        assertTrue(result.success());
        assertEquals(Set.of("active"), reloaded);
        assertEquals(Set.of("active", "new"), loaded);
    }

    @Test
    void reportsConfigurationFailureBeforeMutatingTheGraph() {
        IllegalStateException failure = new IllegalStateException("invalid");
        FeatureGraphReloadResult result = FeatureGraphReloader.reload(
                () -> { throw failure; },
                () -> { throw new AssertionError("must not reset"); },
                Set::<String>of,
                ignored -> false,
                ignored -> false,
                ignored -> { throw new AssertionError("must not disable"); },
                ignored -> { throw new AssertionError("must not reload"); },
                Set::<String>of,
                ignored -> { throw new AssertionError("must not enable"); }
        );

        assertEquals(FeatureGraphReloadResult.Stage.CONFIGURATION, result.stage());
        assertSame(failure, result.failure().orElseThrow());
    }
}
