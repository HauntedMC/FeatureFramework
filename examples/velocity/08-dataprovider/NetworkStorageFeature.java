package com.example.dataproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.integration.dataprovider.DataProviderResources;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "NetworkStorage", version = "1.0.0", enabledByDefault = true,
        requiresPlugins = "dataprovider", requiresResourceExtensions = DataProviderResources.class)
public final class NetworkStorageFeature extends VelocityFeature<ProxyPlugin> {
    public NetworkStorageFeature(VelocityFeatureContext<ProxyPlugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        DataProviderResources data = resources().extensions().require(DataProviderResources.KEY);

        data.registerRedisMessagingProvider("network", "hauntedmc")
                .orElseThrow(() -> new IllegalStateException(
                        "Required Redis messaging connection is unavailable"));

        logger().info("NetworkStorage data resources are ready");
    }

    @Override
    public void disable() {
        // DataProviderResources is cleaned up by VelocityFeatureResources.
    }
}
