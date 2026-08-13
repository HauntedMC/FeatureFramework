package com.example.proxy;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class FeatureDefinitions {
    private FeatureDefinitions() { }

    public static FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> all() {
        var directory = simple("ServerDirectory", ServerDirectoryFeature.class, ServerDirectoryFeature::new);
        var discord = simple("DiscordBridge", DiscordBridgeFeature.class, DiscordBridgeFeature::new);

        var queue = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "Queue", "1.0.0", QueueFeature.class, QueueFeature::new)
                .requiresFeatures("ServerDirectory")
                .optionallyUsesFeatures("DiscordBridge")
                .enabledByDefault()
                .build();

        var permissions = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "LuckPermsBridge", "1.0.0", LuckPermsBridgeFeature.class, LuckPermsBridgeFeature::new)
                .requiresPlugins("luckperms")
                .enabledByDefault()
                .build();

        return FeatureCollection.of(directory, discord, queue, permissions);
    }

    private static FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> simple(
            String name,
            Class<? extends VelocityFeature<Object, Void>> type,
            java.util.function.Function<VelocityFeatureContext<Object, Void>, ? extends VelocityFeature<Object, Void>> factory
    ) {
        return FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        name, "1.0.0", type, factory)
                .enabledByDefault()
                .build();
    }
}
