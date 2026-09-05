package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.loader.FeatureManifestDefinition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Typed, reusable definition of one feature in a host plugin.
 *
 * <p>This replaces the manifest records that applications previously had to reimplement. Required
 * feature dependencies may be declared directly or derived from required capability/internal-service
 * relationships by {@link nl.hauntedmc.featureframework.loader.FeatureManifestDiscovery}.</p>
 */
public final class FeatureDefinition<F extends Feature, C>
        implements FeatureManifestDefinition<ResolvedFeatureDefinition<F, C>> {

    private final String featureName;
    private final String featureVersion;
    private final Class<? extends F> implementationType;
    private final Function<C, ? extends F> constructor;
    private final FeatureStartupPhase startupPhase;
    private final FeatureScope scope;
    private final FeaturePlacement placement;
    private final boolean enabledByDefault;
    private final Set<FeatureRole> roles;
    private final Set<String> requiredFeatures;
    private final Set<String> optionalFeatures;
    private final Set<String> pluginDependencies;
    private final Set<Class<?>> requiredCapabilities;
    private final Set<Class<?>> optionalCapabilities;
    private final Set<Class<?>> providedCapabilities;
    private final Set<Class<?>> requiredInternalServices;
    private final Set<Class<?>> optionalInternalServices;
    private final Set<Class<?>> providedInternalServices;
    private final Set<Class<?>> requiredResourceExtensions;
    private final Set<Class<?>> optionalResourceExtensions;

    private FeatureDefinition(Builder<F, C> builder) {
        featureName = requireText(builder.featureName, "featureName");
        featureVersion = requireText(builder.featureVersion, "featureVersion");
        implementationType = Objects.requireNonNull(builder.implementationType, "implementationType");
        constructor = Objects.requireNonNull(builder.constructor, "constructor");
        startupPhase = builder.startupPhase;
        scope = Objects.requireNonNull(builder.scope, "scope");
        placement = Objects.requireNonNull(builder.placement, "placement");
        enabledByDefault = builder.enabledByDefault;
        requiredFeatures = immutableText(builder.requiredFeatures, "requiredFeatures");
        optionalFeatures = withoutRequired(
                immutableText(builder.optionalFeatures, "optionalFeatures"), requiredFeatures);
        pluginDependencies = immutableText(builder.pluginDependencies, "pluginDependencies");
        requiredCapabilities = immutableTypes(builder.requiredCapabilities, "requiredCapabilities");
        optionalCapabilities = withoutRequiredTypes(
                immutableTypes(builder.optionalCapabilities, "optionalCapabilities"), requiredCapabilities);
        providedCapabilities = immutableTypes(builder.providedCapabilities, "providedCapabilities");
        requiredInternalServices = immutableTypes(
                builder.requiredInternalServices, "requiredInternalServices");
        optionalInternalServices = withoutRequiredTypes(
                immutableTypes(builder.optionalInternalServices, "optionalInternalServices"),
                requiredInternalServices
        );
        providedInternalServices = immutableTypes(
                builder.providedInternalServices, "providedInternalServices");
        requiredResourceExtensions = immutableTypes(
                builder.requiredResourceExtensions, "requiredResourceExtensions");
        optionalResourceExtensions = withoutRequiredTypes(
                immutableTypes(builder.optionalResourceExtensions, "optionalResourceExtensions"),
                requiredResourceExtensions);
        roles = effectiveRoles(builder.roles, requiredCapabilities, optionalCapabilities, providedCapabilities);
        ensureDisjoint(requiredCapabilities, providedCapabilities, "required", "provided capability");
        ensureDisjoint(optionalCapabilities, providedCapabilities, "optional", "provided capability");
        ensureDisjoint(requiredInternalServices, providedInternalServices, "required", "provided internal service");
        ensureDisjoint(optionalInternalServices, providedInternalServices, "optional", "provided internal service");
        if (roles.contains(FeatureRole.EXTENSION_PROVIDER) && providedCapabilities.isEmpty()) {
            throw new IllegalArgumentException("Extension providers must declare a provided capability");
        }
    }

    public static <F extends Feature, C> Builder<F, C> builder(
            String featureName,
            String featureVersion,
            Class<? extends F> implementationType,
            Function<C, ? extends F> constructor
    ) {
        return new Builder<>(featureName, featureVersion, implementationType, constructor);
    }

    @Override public String featureName() { return featureName; }
    public String featureVersion() { return featureVersion; }
    public Class<? extends F> implementationType() { return implementationType; }
    public Function<C, ? extends F> constructor() { return constructor; }
    @Override public FeatureStartupPhase startupPhase() { return startupPhase; }
    @Override public FeatureScope scope() { return scope; }
    @Override public FeaturePlacement placement() { return placement; }
    public boolean enabledByDefault() { return enabledByDefault; }
    public Set<String> requiredFeatures() { return requiredFeatures; }
    public Set<String> optionalFeatureDependencies() { return optionalFeatures; }
    public Set<String> pluginDependencies() { return pluginDependencies; }

    @Override public Set<FeatureRole> roles() { return roles; }
    @Override public Set<Class<?>> requiredCapabilities() { return requiredCapabilities; }
    @Override public Set<Class<?>> optionalCapabilities() { return optionalCapabilities; }
    @Override public Set<Class<?>> providedCapabilities() { return providedCapabilities; }
    @Override public Set<Class<?>> requiredInternalServices() { return requiredInternalServices; }
    @Override public Set<Class<?>> optionalInternalServices() { return optionalInternalServices; }
    @Override public Set<Class<?>> providedInternalServices() { return providedInternalServices; }
    @Override public Set<Class<?>> requiredResourceExtensions() { return requiredResourceExtensions; }
    @Override public Set<Class<?>> optionalResourceExtensions() { return optionalResourceExtensions; }

    @Override
    public ResolvedFeatureDefinition<F, C> descriptor(Set<String> discoveredDependencies) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>(requiredFeatures);
        dependencies.addAll(Objects.requireNonNull(discoveredDependencies, "discoveredDependencies"));
        return new ResolvedFeatureDefinition<>(
                featureName,
                featureName,
                featureVersion,
                implementationType,
                constructor,
                dependencies,
                optionalFeatures,
                pluginDependencies,
                requiredResourceExtensions,
                optionalResourceExtensions,
                placement,
                requiredCapabilities,
                optionalCapabilities,
                providedCapabilities,
                requiredInternalServices,
                optionalInternalServices,
                providedInternalServices
        );
    }

    /** Fluent builder for one feature definition. */
    public static final class Builder<F extends Feature, C> {
        private final String featureName;
        private final String featureVersion;
        private final Class<? extends F> implementationType;
        private final Function<C, ? extends F> constructor;
        private FeatureStartupPhase startupPhase = FeatureStartupPhase.CORE;
        private FeatureScope scope = FeatureScope.NODE;
        private FeaturePlacement placement = FeaturePlacement.ALL_NODES;
        private boolean enabledByDefault;
        private final Set<FeatureRole> roles = new LinkedHashSet<>();
        private final Set<String> requiredFeatures = new LinkedHashSet<>();
        private final Set<String> optionalFeatures = new LinkedHashSet<>();
        private final Set<String> pluginDependencies = new LinkedHashSet<>();
        private final Set<Class<?>> requiredCapabilities = new LinkedHashSet<>();
        private final Set<Class<?>> optionalCapabilities = new LinkedHashSet<>();
        private final Set<Class<?>> providedCapabilities = new LinkedHashSet<>();
        private final Set<Class<?>> requiredInternalServices = new LinkedHashSet<>();
        private final Set<Class<?>> optionalInternalServices = new LinkedHashSet<>();
        private final Set<Class<?>> providedInternalServices = new LinkedHashSet<>();
        private final Set<Class<?>> requiredResourceExtensions = new LinkedHashSet<>();
        private final Set<Class<?>> optionalResourceExtensions = new LinkedHashSet<>();

        private Builder(
                String featureName,
                String featureVersion,
                Class<? extends F> implementationType,
                Function<C, ? extends F> constructor
        ) {
            this.featureName = featureName;
            this.featureVersion = featureVersion;
            this.implementationType = implementationType;
            this.constructor = constructor;
        }

        public Builder<F, C> startupPhase(FeatureStartupPhase value) {
            startupPhase = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<F, C> scope(FeatureScope value) {
            scope = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<F, C> placement(FeaturePlacement value) {
            placement = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<F, C> enabledByDefault() { enabledByDefault = true; return this; }
        public Builder<F, C> roles(FeatureRole... values) { addAll(roles, values, "roles"); return this; }
        public Builder<F, C> requiresFeatures(String... values) { addAll(requiredFeatures, values, "requiredFeatures"); return this; }
        public Builder<F, C> optionallyUsesFeatures(String... values) { addAll(optionalFeatures, values, "optionalFeatures"); return this; }
        public Builder<F, C> requiresPlugins(String... values) { addAll(pluginDependencies, values, "pluginDependencies"); return this; }
        public Builder<F, C> requiresCapabilities(Class<?>... values) { addAll(requiredCapabilities, values, "requiredCapabilities"); return this; }
        public Builder<F, C> optionallyUsesCapabilities(Class<?>... values) { addAll(optionalCapabilities, values, "optionalCapabilities"); return this; }
        public Builder<F, C> providesCapabilities(Class<?>... values) { addAll(providedCapabilities, values, "providedCapabilities"); return this; }
        public Builder<F, C> requiresInternalServices(Class<?>... values) { addAll(requiredInternalServices, values, "requiredInternalServices"); return this; }
        public Builder<F, C> optionallyUsesInternalServices(Class<?>... values) { addAll(optionalInternalServices, values, "optionalInternalServices"); return this; }
        public Builder<F, C> providesInternalServices(Class<?>... values) { addAll(providedInternalServices, values, "providedInternalServices"); return this; }
        public Builder<F, C> requiresResourceExtensions(Class<?>... values) { addAll(requiredResourceExtensions, values, "requiredResourceExtensions"); return this; }
        public Builder<F, C> optionallyUsesResourceExtensions(Class<?>... values) { addAll(optionalResourceExtensions, values, "optionalResourceExtensions"); return this; }
        public FeatureDefinition<F, C> build() { return new FeatureDefinition<>(this); }
    }

    private static Set<FeatureRole> effectiveRoles(
            Set<FeatureRole> declared,
            Set<Class<?>> requiredCapabilities,
            Set<Class<?>> optionalCapabilities,
            Set<Class<?>> providedCapabilities
    ) {
        EnumSet<FeatureRole> result = declared.isEmpty()
                ? EnumSet.noneOf(FeatureRole.class) : EnumSet.copyOf(declared);
        if (!providedCapabilities.isEmpty()) result.add(FeatureRole.CAPABILITY_PROVIDER);
        if (!requiredCapabilities.isEmpty() || !optionalCapabilities.isEmpty()) result.add(FeatureRole.CAPABILITY_CONSUMER);
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    private static Set<String> immutableText(Set<String> values, String field) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(values, field)) {
            String clean = requireText(value, field);
            if (normalized.stream().noneMatch(clean::equalsIgnoreCase)) normalized.add(clean);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<Class<?>> immutableTypes(Set<Class<?>> values, String field) {
        LinkedHashSet<Class<?>> normalized = new LinkedHashSet<>();
        for (Class<?> value : Objects.requireNonNull(values, field)) normalized.add(Objects.requireNonNull(value, field + " entry"));
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<String> withoutRequired(Set<String> optional, Set<String> required) {
        LinkedHashSet<String> result = new LinkedHashSet<>(optional);
        result.removeIf(candidate -> required.stream().anyMatch(candidate::equalsIgnoreCase));
        return Collections.unmodifiableSet(result);
    }

    private static Set<Class<?>> withoutRequiredTypes(Set<Class<?>> optional, Set<Class<?>> required) {
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>(optional);
        result.removeAll(required);
        return Collections.unmodifiableSet(result);
    }

    private static void ensureDisjoint(Set<Class<?>> first, Set<Class<?>> second, String firstName, String secondName) {
        for (Class<?> type : first) {
            if (second.contains(type)) throw new IllegalArgumentException(type.getName() + " cannot be both " + firstName + " and " + secondName);
        }
    }

    private static <T> void addAll(Set<T> target, T[] values, String field) {
        Objects.requireNonNull(values, field);
        for (T value : values) target.add(Objects.requireNonNull(value, field + " entry"));
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
