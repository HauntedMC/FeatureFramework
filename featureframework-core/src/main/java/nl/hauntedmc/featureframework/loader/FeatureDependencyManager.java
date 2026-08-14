package nl.hauntedmc.featureframework.loader;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Platform-neutral dependency loading and reverse-dependency queries. */
public class FeatureDependencyManager {
    private final Function<String, String> keyResolver;
    private final Predicate<String> loaded;
    private final Function<String, ? extends ResolvedFeatureDefinition<?, ?>> descriptorProvider;
    private final Function<String, Boolean> featureLoader;
    private final Function<String, Set<String>> missingPluginDependencies;
    private final Supplier<Set<String>> loadedFeatureNames;
    private final Consumer<String> warningLogger;
    private final Consumer<String> infoLogger;

    public FeatureDependencyManager(
            Function<String, String> keyResolver,
            Predicate<String> loaded,
            Function<String, ? extends ResolvedFeatureDefinition<?, ?>> descriptorProvider,
            Function<String, Boolean> featureLoader,
            Function<String, Set<String>> missingPluginDependencies,
            Supplier<Set<String>> loadedFeatureNames,
            Consumer<String> warningLogger,
            Consumer<String> infoLogger
    ) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        this.descriptorProvider = Objects.requireNonNull(descriptorProvider, "descriptorProvider");
        this.featureLoader = Objects.requireNonNull(featureLoader, "featureLoader");
        this.missingPluginDependencies = Objects.requireNonNull(
                missingPluginDependencies, "missingPluginDependencies");
        this.loadedFeatureNames = Objects.requireNonNull(loadedFeatureNames, "loadedFeatureNames");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
        this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
    }

    public boolean areDependenciesMet(String featureName) {
        String featureKey = keyResolver.apply(featureName);
        if (featureKey == null) {
            warningLogger.accept("Feature not found in registry: " + featureName);
            return false;
        }
        return FeatureDependencyTraversal.checkDependencies(
                featureKey,
                new HashSet<>(),
                new HashSet<>(),
                loaded,
                descriptorProvider,
                keyResolver,
                featureLoader,
                warningLogger,
                infoLogger
        ) && arePluginDependenciesMet(featureKey);
    }

    public boolean arePluginDependenciesMet(String featureName) {
        Set<String> missing = missingPluginDependencies.apply(featureName);
        if (missing.isEmpty()) {
            return true;
        }
        warningLogger.accept("Cannot enable " + featureName + " because required plugin(s) "
                + String.join(", ", missing) + " are missing.");
        return false;
    }

    public List<String> getDependentFeatures(String featureName) {
        return FeatureDependentResolver.getDependentFeatures(
                keyResolver.apply(featureName),
                loadedFeatureNames.get(),
                descriptorProvider,
                keyResolver
        );
    }
}
