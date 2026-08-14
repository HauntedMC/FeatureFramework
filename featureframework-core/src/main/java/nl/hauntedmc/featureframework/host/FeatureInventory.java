package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.FeatureConfigurationRoot;
import nl.hauntedmc.featureframework.dependency.DependencyCheckResult;
import nl.hauntedmc.featureframework.feature.LifecycleFeature;
import nl.hauntedmc.featureframework.loader.FeatureDependencyDiagnostics;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.loader.FeatureKeyResolver;
import nl.hauntedmc.featureframework.loader.FeatureLoadOrderResolver;
import nl.hauntedmc.featureframework.loader.FeatureManifestDiscovery;
import nl.hauntedmc.featureframework.loader.FeatureRegistry;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Owns the discovered feature inventory and all metadata-only graph queries.
 *
 * <p>This keeps discovery, key resolution, dependency diagnostics, and plugin availability separate
 * from live feature instance lifecycle. The class is deliberately package-private: it is an internal
 * host implementation detail rather than public framework API.</p>
 */
final class FeatureInventory<F extends LifecycleFeature<C>, C extends FeatureHostContext> {
    private final String capabilityNamespace;
    private final FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime;
    private final FeatureConfigurationRoot<?> configuration;
    private final FeatureCollection<F, C> collection;
    private final Predicate<String> pluginAvailable;
    private final FrameworkLogger logger;
    private final FeatureRegistry<F, ResolvedFeatureDefinition<F, C>> registry = new FeatureRegistry<>();
    private boolean discovered;

    FeatureInventory(
            String capabilityNamespace,
            FeatureRuntime<FeatureId, ? extends DefaultCapabilityRegistry> runtime,
            FeatureConfigurationRoot<?> configuration,
            FeatureCollection<F, C> collection,
            Predicate<String> pluginAvailable,
            FrameworkLogger logger
    ) {
        this.capabilityNamespace = capabilityNamespace;
        this.runtime = runtime;
        this.configuration = configuration;
        this.collection = collection;
        this.pluginAvailable = pluginAvailable;
        this.logger = logger;
    }

    void discover(String hostName) {
        if (discovered) return;
        FeatureManifestDiscovery.Result<ResolvedFeatureDefinition<F, C>, FeatureDefinition<F, C>> result =
                FeatureManifestDiscovery.discover(
                        collection.definitions(), runtime.capabilities().availableTypes(), capabilityNamespace);
        if (!result.conflicts().isEmpty()) {
            throw new IllegalArgumentException("Conflicting feature definitions: " + result.conflicts());
        }

        Map<String, FeatureDefinition<F, C>> definitions = definitionsByName(collection.definitions());
        for (FeatureManifestDiscovery.Discovered<ResolvedFeatureDefinition<F, C>, FeatureDefinition<F, C>> item
                : result.discovered()) {
            ResolvedFeatureDefinition<F, C> descriptor = item.descriptor();
            FeatureDefinition<F, C> definition = definitions.get(normalize(descriptor.registryName()));
            registry.registerAvailableFeature(descriptor);
            configuration.registerFeature(descriptor.registryName(), definition.enabledByDefault());
            runtime.mutableFeatureCatalog().register(item.publicDescriptor());
            runtime.mutableFeatureCatalog().setConfiguredEnabled(
                    FeatureId.of(descriptor.registryName()), configuration.isFeatureEnabled(descriptor.registryName()));
        }
        pruneMissingFeatureDependencies();
        discovered = true;
        logger.info("[" + hostName + "] Registered features: " + registry.getAvailableFeatures().keySet());
    }

    FeatureRegistry<F, ResolvedFeatureDefinition<F, C>> registry() {
        return registry;
    }

    FeatureLoadOrderResolver.Result loadOrder(Collection<String> featureNames) {
        return FeatureLoadOrderResolver.resolveLoadOrder(
                featureNames, registry::getAvailableFeature, this::resolveFeatureKey, logger::error);
    }

    DependencyCheckResult diagnoseDependencies(String featureName) {
        return FeatureDependencyDiagnostics.diagnoseDependenciesRecursively(
                featureName,
                this::resolveFeatureKey,
                registry::getAvailableFeature,
                registry::isFeatureLoaded,
                this::missingPluginDependencies
        );
    }

    Set<String> missingPluginDependencies(String featureName) {
        String key = resolveFeatureKey(featureName);
        ResolvedFeatureDefinition<F, C> descriptor = key == null ? null : registry.getAvailableFeature(key);
        if (descriptor == null) return Set.of();
        return descriptor.pluginDependencies().stream()
                .filter(pluginAvailable.negate())
                .collect(Collectors.toUnmodifiableSet());
    }

    String resolveFeatureKey(String inputName) {
        return FeatureKeyResolver.resolveFeatureKey(
                inputName,
                registry.getAvailableFeatures(),
                registry.getLoadedFeatureNames(),
                key -> {
                    F feature = registry.getLoadedFeature(key);
                    return feature == null ? null : feature.name();
                }
        );
    }

    private void pruneMissingFeatureDependencies() {
        boolean changed;
        do {
            changed = false;
            Set<String> available = new LinkedHashSet<>(registry.getAvailableFeatures().keySet());
            for (ResolvedFeatureDefinition<F, C> descriptor : new ArrayList<>(registry.getAvailableFeatures().values())) {
                Set<String> missing = descriptor.featureDependencies().stream()
                        .filter(dependency -> resolveAvailableKey(dependency, available) == null)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (missing.isEmpty()) continue;
                registry.deregisterAvailableFeature(descriptor.registryName());
                runtime.mutableFeatureCatalog().setUnavailableDependencies(
                        FeatureId.of(descriptor.registryName()),
                        missing.stream().map(FeatureId::of).collect(Collectors.toUnmodifiableSet()));
                logger.error("Skipping feature '" + descriptor.registryName()
                        + "' because dependencies are unavailable: " + String.join(", ", missing));
                changed = true;
            }
        } while (changed);
    }

    private static <F extends LifecycleFeature<C>, C extends FeatureHostContext>
    Map<String, FeatureDefinition<F, C>> definitionsByName(Collection<FeatureDefinition<F, C>> definitions) {
        Map<String, FeatureDefinition<F, C>> result = new LinkedHashMap<>();
        definitions.forEach(definition -> result.put(normalize(definition.featureName()), definition));
        return result;
    }

    private static String resolveAvailableKey(String requested, Set<String> available) {
        return available.stream().filter(requested::equalsIgnoreCase).findFirst().orElse(null);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
