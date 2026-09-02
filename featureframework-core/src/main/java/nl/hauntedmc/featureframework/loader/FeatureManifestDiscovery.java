package nl.hauntedmc.featureframework.loader;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates and materializes an explicit feature inventory without knowing any concrete feature. */
public final class FeatureManifestDiscovery {
    private FeatureManifestDiscovery() { }

    public static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>> Result<D, E> discover(
            Collection<E> manifest,
            Set<Class<?>> bootstrapCapabilities,
            String capabilityNamespace
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Set<Class<?>> bootstrap = bootstrapCapabilities == null ? Set.of() : Set.copyOf(bootstrapCapabilities);
        String namespace = requireText(capabilityNamespace, "capabilityNamespace");
        List<E> definitions = new ArrayList<>(manifest);
        definitions.sort(Comparator.comparing(FeatureManifestDefinition::startupPhase));

        Map<Class<?>, E> capabilityProviders = uniqueProviders(
                definitions, FeatureManifestDefinition::providedCapabilities, "Capability");
        Map<Class<?>, E> internalProviders = uniqueProviders(
                definitions, FeatureManifestDefinition::providedInternalServices, "Internal service");
        validateReferences(definitions, capabilityProviders, bootstrap, false);
        validateReferences(definitions, internalProviders, Set.of(), true);

        List<Discovered<D, E>> discovered = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, Discovered<D, E>> byNormalizedKey = new LinkedHashMap<>();
        for (E definition : definitions) {
            Set<String> dependencies = resolveDependencies(definition, capabilityProviders, internalProviders, bootstrap);
            D descriptor = Objects.requireNonNull(definition.descriptor(dependencies), "definition descriptor");
            String normalized = descriptor.registryName().toLowerCase(Locale.ROOT);
            Discovered<D, E> previous = byNormalizedKey.get(normalized);
            if (previous != null) {
                conflicts.add(new Conflict(descriptor.registryName(), descriptor.implementationType(),
                        previous.descriptor().implementationType()));
                continue;
            }
            Discovered<D, E> item = new Discovered<>(
                    descriptor, definition, publicDescriptor(descriptor, definition, namespace));
            byNormalizedKey.put(normalized, item);
            discovered.add(item);
        }
        validatePlacementDependencies(byNormalizedKey);
        return new Result<>(List.copyOf(discovered), List.copyOf(conflicts));
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>>
    void validatePlacementDependencies(Map<String, Discovered<D, E>> discovered) {
        for (Discovered<D, E> item : discovered.values()) {
            if (item.definition().placement() != FeaturePlacement.ALL_NODES) continue;
            for (String dependency : item.descriptor().featureDependencies()) {
                Discovered<D, E> provider = discovered.get(dependency.toLowerCase(Locale.ROOT));
                if (provider != null && provider.definition().placement() == FeaturePlacement.GROUP_LEADER_ONLY) {
                    throw new IllegalStateException("ALL_NODES feature " + item.definition().featureName()
                            + " cannot require GROUP_LEADER_ONLY feature " + provider.definition().featureName());
                }
            }
        }
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>>
    Map<Class<?>, E> uniqueProviders(
            Collection<E> definitions,
            java.util.function.Function<E, Set<Class<?>>> providedTypes,
            String kind
    ) {
        Map<Class<?>, E> providers = new LinkedHashMap<>();
        for (E definition : definitions) {
            for (Class<?> type : providedTypes.apply(definition)) {
                E previous = providers.putIfAbsent(type, definition);
                if (previous != null) {
                    throw new IllegalStateException(kind + " " + type.getName() + " is provided by both "
                            + previous.featureName() + " and " + definition.featureName());
                }
            }
        }
        return Map.copyOf(providers);
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>> void validateReferences(
            Collection<E> definitions,
            Map<Class<?>, E> providers,
            Set<Class<?>> bootstrap,
            boolean internal
    ) {
        for (E definition : definitions) {
            LinkedHashSet<Class<?>> referenced = new LinkedHashSet<>(internal
                    ? definition.requiredInternalServices() : definition.requiredCapabilities());
            referenced.addAll(internal ? definition.optionalInternalServices() : definition.optionalCapabilities());
            for (Class<?> type : referenced) {
                if (!providers.containsKey(type) && !bootstrap.contains(type)) {
                    throw new IllegalStateException("Feature " + definition.featureName() + " references "
                            + (internal ? "internal service" : "capability") + " without a provider: " + type.getName());
                }
            }
        }
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>> Set<String> resolveDependencies(
            E definition,
            Map<Class<?>, E> capabilityProviders,
            Map<Class<?>, E> internalProviders,
            Set<Class<?>> bootstrap
    ) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (Class<?> type : definition.requiredCapabilities()) {
            E provider = capabilityProviders.get(type);
            if (provider == null && bootstrap.contains(type)) continue;
            addProviderDependency(definition, provider, dependencies);
        }
        for (Class<?> type : definition.requiredInternalServices()) {
            addProviderDependency(definition, internalProviders.get(type), dependencies);
        }
        return Set.copyOf(dependencies);
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>> void addProviderDependency(
            E consumer,
            E provider,
            Set<String> dependencies
    ) {
        if (provider != null && !provider.featureName().equalsIgnoreCase(consumer.featureName())) {
            dependencies.add(provider.featureName());
        }
    }

    private static <D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>>
    nl.hauntedmc.featureframework.api.feature.FeatureMetadata publicDescriptor(
            D descriptor,
            E definition,
            String namespace
    ) {
        Set<FeatureId> dependencies = descriptor.featureDependencies().stream()
                .map(FeatureId::of).collect(Collectors.toUnmodifiableSet());
        Set<String> capabilities = definition.providedCapabilities().stream()
                .map(type -> capabilityId(namespace, type)).collect(Collectors.toUnmodifiableSet());
        Set<String> resources = definition.requiredResourceExtensions().stream()
                .map(Class::getName).collect(Collectors.toUnmodifiableSet());
        return new nl.hauntedmc.featureframework.api.feature.FeatureMetadata(
                FeatureId.of(descriptor.registryName()), descriptor.featureName(), descriptor.featureVersion(),
                dependencies, descriptor.pluginDependencies(), resources, capabilities, definition.roles(),
                definition.scope(), definition.placement());
    }

    private static String capabilityId(String namespace, Class<?> capability) {
        String simple = capability.getSimpleName();
        String base = simple.endsWith("Api") ? simple.substring(0, simple.length() - 3) : simple;
        return namespace + ":" + base.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    public record Discovered<D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>>(
            D descriptor,
            E definition,
            nl.hauntedmc.featureframework.api.feature.FeatureMetadata publicDescriptor
    ) { }

    public record Conflict(String registryName, Class<?> rejectedType, Class<?> existingType) { }

    public record Result<D extends ResolvedFeatureDefinition<?, ?>, E extends FeatureManifestDefinition<D>>(
            List<Discovered<D, E>> discovered,
            List<Conflict> conflicts
    ) { }
}
