package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.feature.Feature;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable, reflection-free description of a feature implementation and its dependencies.
 *
 * @param <F> feature implementation base type
 * @param <C> construction context type
 */
public class FeatureDescriptor<F extends Feature, C> {
    private final String registryName;
    private final String featureName;
    private final String featureVersion;
    private final Class<? extends F> implementationType;
    private final Function<C, ? extends F> constructor;
    private final Set<String> featureDependencies;
    private final Set<String> optionalFeatureDependencies;
    private final Set<String> pluginDependencies;

    public FeatureDescriptor(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> pluginDependencies
    ) {
        this(
                registryName,
                featureName,
                featureVersion,
                implementationType,
                constructor,
                featureDependencies,
                Set.of(),
                pluginDependencies
        );
    }

    public FeatureDescriptor(
            String registryName,
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor,
            Set<String> featureDependencies,
            Set<String> optionalFeatureDependencies,
            Set<String> pluginDependencies
    ) {
        this.registryName = requireText(registryName, "registryName");
        this.featureName = requireText(featureName, "featureName");
        this.featureVersion = requireText(featureVersion, "featureVersion");
        this.implementationType = Objects.requireNonNull(implementationType, "implementationType");
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.featureDependencies = normalizeDependencies(featureDependencies, registryName);
        this.optionalFeatureDependencies = withoutRequiredDependencies(
                normalizeDependencies(optionalFeatureDependencies, registryName),
                this.featureDependencies
        );
        this.pluginDependencies = normalizeDependencies(pluginDependencies, null);
    }

    public String registryName() {
        return registryName;
    }

    public String featureName() {
        return featureName;
    }

    public String featureVersion() {
        return featureVersion;
    }

    public Class<? extends F> implementationType() {
        return implementationType;
    }

    public Set<String> featureDependencies() {
        return featureDependencies;
    }

    public Set<String> optionalFeatureDependencies() {
        return optionalFeatureDependencies;
    }

    public Set<String> pluginDependencies() {
        return pluginDependencies;
    }

    public F create(C context) {
        F feature = constructor.apply(Objects.requireNonNull(context, "context"));
        if (feature == null) {
            throw new IllegalStateException("Feature constructor returned null: " + implementationType.getName());
        }
        if (!implementationType.isInstance(feature)) {
            throw new IllegalStateException(
                    "Feature constructor returned " + feature.getClass().getName()
                            + " instead of " + implementationType.getName()
            );
        }
        return feature;
    }

    private static Set<String> withoutRequiredDependencies(Set<String> optional, Set<String> required) {
        if (optional.isEmpty() || required.isEmpty()) {
            return optional;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>(optional);
        result.removeIf(candidate -> required.stream().anyMatch(candidate::equalsIgnoreCase));
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    private static String requireText(String value, String fieldName) {
        String clean = Objects.requireNonNull(value, fieldName).trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return clean;
    }

    private static Set<String> normalizeDependencies(Set<String> dependencies, String selfDependencyName) {
        if (dependencies == null || dependencies.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            String clean = requireText(dependency, "dependency");
            boolean duplicate = normalized.stream().anyMatch(clean::equalsIgnoreCase);
            if (!duplicate && (selfDependencyName == null || !clean.equalsIgnoreCase(selfDependencyName))) {
                normalized.add(clean);
            }
        }
        return normalized.isEmpty() ? Set.of() : Collections.unmodifiableSet(normalized);
    }
}
