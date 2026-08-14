package nl.hauntedmc.featureframework.loader;


import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class FeatureDependencyTraversal {

    private FeatureDependencyTraversal() {
    }

    public static boolean checkDependencies(
            String featureName,
            Set<String> activePath,
            Set<String> resolved,
            Predicate<String> isFeatureLoaded,
            Function<String, ? extends ResolvedFeatureDefinition<?, ?>> descriptorProvider,
            Function<String, String> featureKeyResolver,
            Function<String, Boolean> loadFeature,
            Consumer<String> warningLogger,
            Consumer<String> infoLogger
    ) {
        if (isFeatureLoaded.test(featureName)) {
            resolved.add(featureName);
            return true;
        }
        if (resolved.contains(featureName)) {
            return true;
        }

        if (!activePath.add(featureName)) {
            warningLogger.accept("Circular dependency detected: " + featureName);
            return false;
        }

        try {
            ResolvedFeatureDefinition<?, ?> descriptor = descriptorProvider.apply(featureName);
            if (descriptor == null) {
                warningLogger.accept("Feature not found in registry: " + featureName);
                return false;
            }

            for (String dependency : descriptor.featureDependencies()) {
                String dependencyKey = featureKeyResolver.apply(dependency);
                if (dependencyKey == null) {
                    warningLogger.accept("Missing dependency '" + dependency + "' for feature " + featureName);
                    return false;
                }

                if (!checkDependencies(
                        dependencyKey,
                        activePath,
                        resolved,
                        isFeatureLoaded,
                        descriptorProvider,
                        featureKeyResolver,
                        loadFeature,
                        warningLogger,
                        infoLogger
                )) {
                    return false;
                }

                if (!isFeatureLoaded.test(dependencyKey)) {
                    infoLogger.accept("Enabling dependency " + dependencyKey + " for " + featureName);
                    if (!loadFeature.apply(dependencyKey)) {
                        return false;
                    }
                }
            }

            resolved.add(featureName);
            return true;
        } finally {
            activePath.remove(featureName);
        }
    }
}
