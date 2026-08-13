package com.example.dataproxy;

import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkStorageFeature extends VelocityFeature<ProxyPlugin, FeatureDataManager> {
    public NetworkStorageFeature(VelocityFeatureContext<ProxyPlugin, FeatureDataManager> context) {
        super(context);
    }

    @Override
    public void initialize() {
        FeatureDataManager data = resources().getDataManager();

        data.registerRedisMessagingProvider("network", "hauntedmc")
                .orElseThrow(() -> new IllegalStateException(
                        "Required Redis messaging connection is unavailable"));

        logger().info("NetworkStorage data resources are ready");
    }

    @Override
    public void disable() {
        // FeatureDataManager is cleaned up by VelocityFeatureResources.
    }
}
