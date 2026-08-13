package com.example.velocitydata;

import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkStorageFeature extends VelocityFeature<ProxyPlugin, FeatureDataManager> {
    public NetworkStorageFeature(VelocityFeatureContext<ProxyPlugin, FeatureDataManager> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getDataManager()
                .registerRedisMessagingProvider("network", "hauntedmc")
                .orElseThrow(() -> new IllegalStateException(
                        "Required Redis messaging connection is unavailable"));
    }

    @Override
    public void disable() {
        // The feature resource scope closes FeatureDataManager automatically.
    }
}
