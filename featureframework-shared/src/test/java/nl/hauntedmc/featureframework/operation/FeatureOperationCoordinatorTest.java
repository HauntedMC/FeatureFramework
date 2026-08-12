package nl.hauntedmc.featureframework.operation;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.dependency.DependencyCheckResult;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResult;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResult;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FeatureOperationCoordinatorTest {
    @Test
    void failedEnableRollsBackConfiguredState() {
        AtomicBoolean configured = new AtomicBoolean(false);
        var response = FeatureOperationCoordinator.enable(
                "demo", value -> value, value -> true, value -> false,
                value -> new DependencyCheckResult(Set.of(), Set.of()),
                value -> configured.get(),
                (value, enabled) -> configured.set(enabled),
                value -> false,
                () -> { }
        );

        assertEquals(FeatureEnableResult.FAILED, response.result());
        assertFalse(configured.get());
    }

    @Test
    void disableAggregatesTransitiveDependentsAndAlwaysRunsMutationHook() {
        List<String> stopped = new ArrayList<>();
        AtomicBoolean hookRun = new AtomicBoolean();
        var response = FeatureOperationCoordinator.disable(
                "root", value -> value, value -> true,
                value -> List.of("child"),
                value -> new nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse(
                        FeatureDisableResult.SUCCESS, value, Set.of("grandchild")),
                value -> { stopped.add(value); return null; },
                value -> { },
                (value, failure) -> { },
                () -> hookRun.set(true)
        );

        assertTrue(response.success());
        assertEquals(Set.of("child", "grandchild"), response.alsoDisabledDependents());
        assertEquals(List.of("root"), stopped);
        assertTrue(hookRun.get());
    }

    @Test
    void softReloadRecreatesWhenConfigurationRequiresIt() {
        var response = FeatureOperationCoordinator.softReload(
                "demo", value -> value, value -> true,
                value -> ConfigReloadResult.RECREATE_REQUIRED,
                value -> new FeatureReloadResponse(FeatureReloadResult.SUCCESS, value, Set.of())
        );

        assertEquals(FeatureSoftReloadResult.SUCCESS, response.result());
    }
}
