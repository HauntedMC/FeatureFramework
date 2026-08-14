package nl.hauntedmc.featureframework.paper.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.integration.dataregistry.DataRegistryResources;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResources;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;

import java.util.Objects;
import java.util.function.Supplier;

/** Attaches DataRegistry access when its declaration requests it. */
public final class PaperDataRegistryContributor {
    private PaperDataRegistryContributor() { }

    public static FeatureResourceContributor<PaperFeatureResources> create(Supplier<DataRegistryApi> registry) {
        Objects.requireNonNull(registry, "registry");
        return contributor((request, resources) -> resources.extensions().register(
                DataRegistryResources.KEY,
                new DataRegistryResources(Objects.requireNonNull(
                        registry.get(), "DataRegistry is unavailable for " + request.id().value()))
        ));
    }

    /** Contributes the extension when discovery succeeds, allowing hosts with an optional plugin dependency. */
    public static FeatureResourceContributor<PaperFeatureResources> optional(Supplier<DataRegistryApi> registry) {
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

    private static FeatureResourceContributor<PaperFeatureResources> contributor(Contribution contribution) {
        return new FeatureResourceContributor<>() {
            @Override public Class<?> extensionType() { return DataRegistryResources.class; }
            @Override public void contribute(FeatureResourceRequest request, PaperFeatureResources resources) {
                contribution.contribute(request, resources);
            }
        };
    }

    @FunctionalInterface
    private interface Contribution {
        void contribute(FeatureResourceRequest request, PaperFeatureResources resources);
    }
}
