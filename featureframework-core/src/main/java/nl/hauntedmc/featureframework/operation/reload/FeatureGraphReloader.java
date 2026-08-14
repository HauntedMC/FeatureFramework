package nl.hauntedmc.featureframework.operation.reload;

import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Platform-neutral policy for transactionally reconciling a running graph with configuration. */
public final class FeatureGraphReloader {
    private FeatureGraphReloader() {
    }

    public static FeatureGraphReloadResult reload(
            Runnable reloadConfiguration,
            Runnable resetRuntimeCaches,
            Supplier<? extends Collection<String>> loadedFeatureNames,
            Predicate<String> isLoaded,
            Predicate<String> configuredEnabled,
            Function<String, FeatureDisableResponse> disableFeature,
            Function<String, FeatureReloadResponse> reloadFeature,
            Supplier<? extends Collection<String>> availableFeatureNames,
            Function<String, FeatureEnableResponse> enableFeature
    ) {
        Objects.requireNonNull(reloadConfiguration, "reloadConfiguration");
        Objects.requireNonNull(resetRuntimeCaches, "resetRuntimeCaches");
        Objects.requireNonNull(loadedFeatureNames, "loadedFeatureNames");
        Objects.requireNonNull(isLoaded, "isLoaded");
        Objects.requireNonNull(configuredEnabled, "configuredEnabled");
        Objects.requireNonNull(disableFeature, "disableFeature");
        Objects.requireNonNull(reloadFeature, "reloadFeature");
        Objects.requireNonNull(availableFeatureNames, "availableFeatureNames");
        Objects.requireNonNull(enableFeature, "enableFeature");

        try {
            reloadConfiguration.run();
        } catch (Throwable failure) {
            return FeatureGraphReloadResult.failed(
                    FeatureGraphReloadResult.Stage.CONFIGURATION, null, failure);
        }

        resetRuntimeCaches.run();
        Set<String> loadedBefore = new LinkedHashSet<>(loadedFeatureNames.get());
        Set<String> processed = new LinkedHashSet<>();

        for (String feature : loadedBefore) {
            if (!isLoaded.test(feature) || configuredEnabled.test(feature)) continue;
            FeatureDisableResponse response = disableFeature.apply(feature);
            processed.add(feature);
            processed.addAll(response.alsoDisabledDependents());
            if (!response.success()) {
                return FeatureGraphReloadResult.failed(
                        FeatureGraphReloadResult.Stage.DISABLE, feature, null);
            }
        }

        for (String feature : loadedBefore) {
            if (processed.contains(feature) || !isLoaded.test(feature)) continue;
            FeatureReloadResponse response = reloadFeature.apply(feature);
            if (!response.success()) {
                return FeatureGraphReloadResult.failed(
                        FeatureGraphReloadResult.Stage.RELOAD, feature, null);
            }
            processed.add(feature);
            processed.addAll(response.reloadedDependents());
        }

        enableConfiguredFeatures(availableFeatureNames, configuredEnabled, isLoaded, enableFeature);
        return FeatureGraphReloadResult.successResult();
    }

    private static void enableConfiguredFeatures(
            Supplier<? extends Collection<String>> availableFeatureNames,
            Predicate<String> configuredEnabled,
            Predicate<String> isLoaded,
            Function<String, FeatureEnableResponse> enableFeature
    ) {
        boolean progress;
        do {
            progress = false;
            for (String feature : availableFeatureNames.get()) {
                if (!configuredEnabled.test(feature) || isLoaded.test(feature)) continue;
                if (enableFeature.apply(feature).success()) progress = true;
            }
        } while (progress);
    }
}
