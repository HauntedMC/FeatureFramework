package nl.hauntedmc.featureframework.velocity.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;

import java.util.Objects;
import java.util.function.Supplier;

/** Attaches DataRegistry access when its declaration requests it. */
public final class VelocityDataRegistryContributor {
    private VelocityDataRegistryContributor() { }

    public static FeatureResourceContributor<VelocityFeatureResources> create(Supplier<DataRegistryApi> registry) {
        Objects.requireNonNull(registry, "registry");
        return contributor((request, resources) -> resources.extensions().register(
                DataRegistryResources.KEY,
                new DataRegistryResources(Objects.requireNonNull(
                        registry.get(), "DataRegistry is unavailable for " + request.id().value()))
        ));
    }

    /** Contributes the extension when discovery succeeds, allowing hosts with an optional plugin dependency. */
    public static FeatureResourceContributor<VelocityFeatureResources> optional(Supplier<DataRegistryApi> registry) {
        Objects.requireNonNull(registry, "registry");
        return contributor((request, resources) -> {
            DataRegistryApi discovered;
            try {
                discovered = registry.get();
            } catch (RuntimeException unavailable) {
                return;
            }
            if (discovered != null) {
                resources.extensions().register(DataRegistryResources.KEY, new DataRegistryResources(discovered));
            }
        });
    }

    private static FeatureResourceContributor<VelocityFeatureResources> contributor(Contribution contribution) {
        return new FeatureResourceContributor<>() {
            @Override public Class<?> extensionType() { return DataRegistryResources.class; }
            @Override public void contribute(FeatureResourceRequest request, VelocityFeatureResources resources) {
                contribution.contribute(request, resources);
            }
        };
    }

    @FunctionalInterface
    private interface Contribution {
        void contribute(FeatureResourceRequest request, VelocityFeatureResources resources);
    }
}
