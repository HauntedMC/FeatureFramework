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
    private final Set<Class<?>> requiredCapabilities;
    private final Set<Class<?>> optionalCapabilities;
    private final Set<Class<?>> providedCapabilities;
    private final Set<Class<?>> requiredInternalServices;
    private final Set<Class<?>> optionalInternalServices;
    private final Set<Class<?>> providedInternalServices;
    private final FeaturePlacement placement;

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
            FeaturePlacement placement,
            Set<Class<?>> requiredCapabilities,
            Set<Class<?>> optionalCapabilities,
            Set<Class<?>> providedCapabilities,
            Set<Class<?>> requiredInternalServices,
            Set<Class<?>> optionalInternalServices,
            Set<Class<?>> providedInternalServices
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
        this.requiredCapabilities = immutableTypes(requiredCapabilities);
        this.optionalCapabilities = withoutRequiredTypes(immutableTypes(optionalCapabilities), this.requiredCapabilities);
        this.providedCapabilities = immutableTypes(providedCapabilities);
        this.requiredInternalServices = immutableTypes(requiredInternalServices);
        this.optionalInternalServices = withoutRequiredTypes(
                immutableTypes(optionalInternalServices), this.requiredInternalServices);
        this.providedInternalServices = immutableTypes(providedInternalServices);
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
    public Set<Class<?>> requiredCapabilities() { return requiredCapabilities; }
    public Set<Class<?>> optionalCapabilities() { return optionalCapabilities; }
    public Set<Class<?>> providedCapabilities() { return providedCapabilities; }
    public Set<Class<?>> requiredInternalServices() { return requiredInternalServices; }
    public Set<Class<?>> optionalInternalServices() { return optionalInternalServices; }
    public Set<Class<?>> providedInternalServices() { return providedInternalServices; }

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

    private static Set<Class<?>> withoutRequiredTypes(Set<Class<?>> optional, Set<Class<?>> required) {
        if (optional.isEmpty() || required.isEmpty()) return optional;
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>(optional);
        result.removeAll(required);
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
