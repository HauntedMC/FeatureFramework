package com.example.velocitydata;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class DataFeatures {
    private DataFeatures() {
    }

    public static FeatureCollection<VelocityFeature<ProxyPlugin, FeatureDataManager>, VelocityFeatureContext<ProxyPlugin, FeatureDataManager>> all() {
        FeatureDefinition<VelocityFeature<ProxyPlugin, FeatureDataManager>, VelocityFeatureContext<ProxyPlugin, FeatureDataManager>> storage =
                FeatureDefinition.<VelocityFeature<ProxyPlugin, FeatureDataManager>, VelocityFeatureContext<ProxyPlugin, FeatureDataManager>>builder(
                                "NetworkStorage", "1.0.0", NetworkStorageFeature.class, NetworkStorageFeature::new)
                        .enabledByDefault()
                        .build();
        return FeatureCollection.of(storage);
    }
}
