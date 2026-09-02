package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.feature.Feature;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Immutable, reflection-free construction descriptor used by the host runtime. */
public class ResolvedFeatureDefinition<F extends Feature, C> {
    private final String registryName;
    private final String featureName;
    private final String featureVersion;
    private final Class<? extends F> implementationType;
    private final Function<C, ? extends F> constructor;
    private final Set<String> featureDependencies;
    private final Set<String> optionalFeatureDependencies;
    private final Set<String> pluginDependencies;
    private final Set<Class<?>> requiredResourceExtensions;
    private final Set<Class<?>> optionalResourceExtensions;
    private final FeaturePlacement placement;

    public ResolvedFeatureDefinition(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> pluginDependencies
    ) {
        this(registryName, featureName, featureVersion, implementationType, constructor, featureDependencies,
                Set.of(), pluginDependencies, Set.of(), Set.of(), FeaturePlacement.ALL_NODES);
    }

    public ResolvedFeatureDefinition(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> optionalFeatureDependencies,
            Set<String> pluginDependencies
    ) {
        this(registryName, featureName, featureVersion, implementationType, constructor, featureDependencies,
                optionalFeatureDependencies, pluginDependencies, Set.of(), Set.of(), FeaturePlacement.ALL_NODES);
    }

    public ResolvedFeatureDefinition(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> optionalFeatureDependencies,
            Set<String> pluginDependencies,
            Set<Class<?>> requiredResourceExtensions,
            Set<Class<?>> optionalResourceExtensions
    ) {
        this(registryName, featureName, featureVersion, implementationType, constructor, featureDependencies,
                optionalFeatureDependencies, pluginDependencies, requiredResourceExtensions,
                optionalResourceExtensions, FeaturePlacement.ALL_NODES);
    }

    public ResolvedFeatureDefinition(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> optionalFeatureDependencies,
            Set<String> pluginDependencies,
            Set<Class<?>> requiredResourceExtensions,
            Set<Class<?>> optionalResourceExtensions,
            FeaturePlacement placement
    ) {
        this.registryName = requireText(registryName, "registryName");
        this.featureName = requireText(featureName, "featureName");
        this.featureVersion = requireText(featureVersion, "featureVersion");
        this.implementationType = Objects.requireNonNull(implementationType, "implementationType");
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.featureDependencies = normalizeDependencies(featureDependencies, registryName);
        this.optionalFeatureDependencies = withoutRequiredDependencies(
                normalizeDependencies(optionalFeatureDependencies, registryName), this.featureDependencies);
        this.pluginDependencies = normalizeDependencies(pluginDependencies, null);
        this.requiredResourceExtensions = immutableTypes(requiredResourceExtensions);
        LinkedHashSet<Class<?>> optionalResources = new LinkedHashSet<>(immutableTypes(optionalResourceExtensions));
        optionalResources.removeAll(this.requiredResourceExtensions);
        this.optionalResourceExtensions = Collections.unmodifiableSet(optionalResources);
        this.placement = placement == null ? FeaturePlacement.ALL_NODES : placement;
    }

    public String registryName() { return registryName; }
    public String featureName() { return featureName; }
    public String featureVersion() { return featureVersion; }
    public Class<? extends F> implementationType() { return implementationType; }
    public Set<String> featureDependencies() { return featureDependencies; }
    public Set<String> optionalFeatureDependencies() { return optionalFeatureDependencies; }
    public Set<String> pluginDependencies() { return pluginDependencies; }
    public Set<Class<?>> requiredResourceExtensions() { return requiredResourceExtensions; }
    public Set<Class<?>> optionalResourceExtensions() { return optionalResourceExtensions; }
    public FeaturePlacement placement() { return placement; }

    public F create(C context) {
        F feature = constructor.apply(Objects.requireNonNull(context, "context"));
        if (feature == null) throw new IllegalStateException("Feature constructor returned null: " + implementationType.getName());
        if (!implementationType.isInstance(feature)) {
            throw new IllegalStateException("Feature constructor returned " + feature.getClass().getName()
                    + " instead of " + implementationType.getName());
        }
        return feature;
    }

    private static Set<String> withoutRequiredDependencies(Set<String> optional, Set<String> required) {
        if (optional.isEmpty() || required.isEmpty()) return optional;
        LinkedHashSet<String> result = new LinkedHashSet<>(optional);
        result.removeIf(candidate -> required.stream().anyMatch(candidate::equalsIgnoreCase));
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    private static String requireText(String value, String fieldName) {
        String clean = Objects.requireNonNull(value, fieldName).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(fieldName + " must not be blank");
        return clean;
    }

    private static Set<String> normalizeDependencies(Set<String> dependencies, String selfDependencyName) {
        if (dependencies == null || dependencies.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            String clean = requireText(dependency, "dependency");
            boolean duplicate = normalized.stream().anyMatch(clean::equalsIgnoreCase);
            if (!duplicate && (selfDependencyName == null || !clean.equalsIgnoreCase(selfDependencyName))) normalized.add(clean);
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }

    private static Set<Class<?>> immutableTypes(Set<Class<?>> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> value : values) result.add(Objects.requireNonNull(value, "resource extension"));
        return Collections.unmodifiableSet(result);
    }
}
