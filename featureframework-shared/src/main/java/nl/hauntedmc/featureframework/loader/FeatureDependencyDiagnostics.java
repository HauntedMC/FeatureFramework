package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.dependency.DependencyCheckResult;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Collects missing feature and external-plugin dependencies across a transitive feature graph. */
public final class FeatureDependencyDiagnostics {
    private FeatureDependencyDiagnostics() { }

    public static DependencyCheckResult diagnoseDependenciesRecursively(
            String featureName,
            Function<String, String> featureKeyResolver,
            Function<String, ? extends FeatureDescriptor<?, ?>> descriptorProvider,
            Predicate<String> isFeatureLoaded,
            Function<String, Set<String>> missingPluginDependenciesProvider
    ) {
        String featureKey = featureKeyResolver.apply(featureName);
        if (featureKey == null) return new DependencyCheckResult(Set.of(), Set.of(featureName));
        Set<String> missingPlugins = new LinkedHashSet<>();
        Set<String> missingFeatures = new LinkedHashSet<>();
        collect(featureKey, featureKeyResolver, descriptorProvider, isFeatureLoaded,
                missingPluginDependenciesProvider, new HashSet<>(), missingPlugins, missingFeatures);
        return new DependencyCheckResult(missingPlugins, missingFeatures);
    }

    private static void collect(
            String featureName,
            Function<String, String> keyResolver,
            Function<String, ? extends FeatureDescriptor<?, ?>> descriptorProvider,
            Predicate<String> isLoaded,
            Function<String, Set<String>> missingPluginsProvider,
            Set<String> visited,
            Set<String> missingPlugins,
            Set<String> missingFeatures
    ) {
        if (!visited.add(featureName)) return;
        FeatureDescriptor<?, ?> descriptor = descriptorProvider.apply(featureName);
        if (descriptor == null) {
            missingFeatures.add(featureName);
            return;
        }
        missingPlugins.addAll(missingPluginsProvider.apply(featureName));
        for (String dependency : descriptor.featureDependencies()) {
            String dependencyKey = keyResolver.apply(dependency);
            if (dependencyKey == null) {
                missingFeatures.add(dependency);
                continue;
            }
            if (!isLoaded.test(dependencyKey)) missingFeatures.add(dependencyKey);
            collect(dependencyKey, keyResolver, descriptorProvider, isLoaded, missingPluginsProvider,
                    visited, missingPlugins, missingFeatures);
        }
    }
}
