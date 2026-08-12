package com.example.proxy;

import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class FeatureDefinitions {
    private FeatureDefinitions() {
    }

    public static FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> queue() {
        return FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "Queue", "1.0.0", QueueFeature.class, QueueFeature::new)
                .requiresFeatures("ServerDirectory")
                .optionallyUsesFeatures("DiscordBridge")
                .enabledByDefault()
                .build();
    }
}
