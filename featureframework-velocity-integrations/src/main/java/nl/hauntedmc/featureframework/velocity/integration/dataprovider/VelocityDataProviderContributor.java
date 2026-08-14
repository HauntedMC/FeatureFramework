package nl.hauntedmc.featureframework.velocity.integration.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderResources;
import nl.hauntedmc.featureframework.resource.FeatureResourceContributor;
import nl.hauntedmc.featureframework.resource.FeatureResourceRequest;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResources;

import java.util.Objects;
import java.util.function.Supplier;

/** Attaches a feature-owned DataProvider scope when its declaration requests it. */
public final class VelocityDataProviderContributor {
    private VelocityDataProviderContributor() { }

    public static FeatureResourceContributor<VelocityFeatureResources> create(
            Object plugin,
            Supplier<DataProviderAPI> api,
            FrameworkLogger logger,
            Supplier<String> schemaMode
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(schemaMode, "schemaMode");
        return new FeatureResourceContributor<>() {
            @Override public Class<?> extensionType() { return DataProviderResources.class; }

            @Override
            public void contribute(FeatureResourceRequest request, VelocityFeatureResources resources) {
                DataProviderResources data = new DataProviderResources(plugin, api, logger, schemaMode);
                data.bindToFeature(request.id().value());
                resources.ownership().ownPhased(data, DataProviderResources::quiesce,
                        DataProviderResources::closeAllDataResources);
                resources.extensions().register(DataProviderResources.KEY, data);
            }
        };
    }
}
