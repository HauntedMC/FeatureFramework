package com.example.proxy;

import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class Definitions {
    private Definitions() {
    }

    public static FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> directory() {
        return FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "NetworkDirectory", "1.0.0", NetworkDirectoryFeature.class, NetworkDirectoryFeature::new)
                .providesCapabilities(NetworkPlayerApi.class)
                .enabledByDefault()
                .build();
    }

    public static FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> consumer() {
        return FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "NetworkCommands", "1.0.0", NetworkCommandsFeature.class, NetworkCommandsFeature::new)
                .requiresCapabilities(NetworkPlayerApi.class)
                .enabledByDefault()
                .build();
    }
}
