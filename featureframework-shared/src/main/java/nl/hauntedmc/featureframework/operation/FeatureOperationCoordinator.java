package nl.hauntedmc.featureframework.operation;

import nl.hauntedmc.featureframework.config.ConfigReloadResult;
import nl.hauntedmc.featureframework.dependency.DependencyCheckResult;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResult;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResult;
import nl.hauntedmc.featureframework.operation.reload.FeatureReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.featureframework.operation.softreload.FeatureSoftReloadResult;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Platform-neutral enable, disable, and soft-reload transaction policy. */
public final class FeatureOperationCoordinator {
    private FeatureOperationCoordinator() { }

    public static FeatureEnableResponse enable(
            String input,
            Function<String, String> keyResolver,
            Predicate<String> isAvailable,
            Predicate<String> isLoaded,
            Function<String, DependencyCheckResult> diagnostics,
            Predicate<String> configuredEnabled,
            BiConsumer<String, Boolean> persistConfiguredEnabled,
            Predicate<String> loadFeature,
            Runnable afterSuccess
    ) {
        String key = keyResolver.apply(input);
        if (key == null || !isAvailable.test(key)) return enable(FeatureEnableResult.NOT_FOUND, noMissingDependencies());
        if (isLoaded.test(key)) return enable(FeatureEnableResult.ALREADY_LOADED, noMissingDependencies());
        DependencyCheckResult before = diagnostics.apply(key);
        if (!before.ok()) return dependencyFailure(before);

        boolean previous = configuredEnabled.test(key);
        persistConfiguredEnabled.accept(key, true);
        if (!loadFeature.test(key)) {
            persistConfiguredEnabled.accept(key, previous);
            DependencyCheckResult after = diagnostics.apply(key);
            return after.ok() ? enable(FeatureEnableResult.FAILED, after) : dependencyFailure(after);
        }
        afterSuccess.run();
        return enable(FeatureEnableResult.SUCCESS, noMissingDependencies());
    }

    public static FeatureDisableResponse disable(
            String input,
            Function<String, String> keyResolver,
            Predicate<String> isLoaded,
            Function<String, ? extends Iterable<String>> dependentProvider,
            Function<String, FeatureDisableResponse> disableDependent,
            Function<String, Throwable> stopAndRemove,
            Consumer<String> persistDisabled,
            BiConsumer<String, Throwable> completeDisable,
            Runnable afterMutation
    ) {
        String key = keyResolver.apply(input);
        if (key == null || !isLoaded.test(key)) {
            return new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, input, Set.of());
        }

        LinkedHashSet<String> disabledDependents = new LinkedHashSet<>();
        Throwable failure = null;
        try {
            for (String dependent : dependentProvider.apply(key)) {
                FeatureDisableResponse response = disableDependent.apply(dependent);
                if (response.feature() != null) disabledDependents.add(response.feature());
                disabledDependents.addAll(response.alsoDisabledDependents());
                if (!response.success()) {
                    failure = append(failure, new IllegalStateException(
                            "Failed to disable dependent feature '" + dependent + "'."));
                }
            }
            try {
                failure = append(failure, stopAndRemove.apply(key));
            } catch (Throwable stopFailure) {
                failure = append(failure, stopFailure);
            }
            try {
                persistDisabled.accept(key);
            } catch (Throwable persistenceFailure) {
                failure = append(failure, persistenceFailure);
            }
            try {
                completeDisable.accept(key, failure);
            } catch (Throwable completionFailure) {
                failure = append(failure, completionFailure);
            }
            return new FeatureDisableResponse(
                    failure == null ? FeatureDisableResult.SUCCESS : FeatureDisableResult.FAILED,
                    key,
                    Set.copyOf(disabledDependents)
            );
        } finally {
            afterMutation.run();
        }
    }

    public static FeatureSoftReloadResponse softReload(
            String input,
            Function<String, String> keyResolver,
            Predicate<String> isLoaded,
            Function<String, ConfigReloadResult> applyConfiguration,
            Function<String, FeatureReloadResponse> recreateFeature
    ) {
        String key = keyResolver.apply(input);
        if (key == null || !isLoaded.test(key)) {
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, input);
        }
        try {
            if (applyConfiguration.apply(key) == ConfigReloadResult.RECREATE_REQUIRED) {
                return new FeatureSoftReloadResponse(
                        recreateFeature.apply(key).success()
                                ? FeatureSoftReloadResult.SUCCESS : FeatureSoftReloadResult.FAILED,
                        key
                );
            }
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, key);
        } catch (Throwable failure) {
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, key);
        }
    }

    private static FeatureEnableResponse dependencyFailure(DependencyCheckResult diagnostics) {
        FeatureEnableResult result = diagnostics.missingPluginDependencies().isEmpty()
                ? FeatureEnableResult.MISSING_FEATURE_DEPENDENCY
                : FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY;
        return enable(result, diagnostics);
    }

    private static DependencyCheckResult noMissingDependencies() {
        return new DependencyCheckResult(Set.of(), Set.of());
    }

    private static FeatureEnableResponse enable(FeatureEnableResult result, DependencyCheckResult diagnostics) {
        return new FeatureEnableResponse(
                result,
                diagnostics.missingPluginDependencies(),
                diagnostics.missingFeatureDependencies()
        );
    }

    private static Throwable append(Throwable current, Throwable additional) {
        if (additional == null) return current;
        if (current == null) return additional;
        current.addSuppressed(additional);
        return current;
    }
}
