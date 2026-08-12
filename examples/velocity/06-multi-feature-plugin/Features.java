package com.example.largeproxy;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class Features {
    private Features() {
    }

    public static FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> all() {
        var directory = simple("ServerDirectory", ServerDirectoryFeature.class, ServerDirectoryFeature::new);
        var maintenance = simple("Maintenance", MaintenanceFeature.class, MaintenanceFeature::new);
        var queue = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "Queue", "1.0.0", QueueFeature.class, QueueFeature::new)
                .requiresFeatures("ServerDirectory")
                .enabledByDefault()
                .build();
        var commands = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "NetworkCommands", "1.0.0", NetworkCommandsFeature.class, NetworkCommandsFeature::new)
                .requiresFeatures("ServerDirectory")
                .optionallyUsesFeatures("Maintenance")
                .enabledByDefault()
                .build();
        return FeatureCollection.of(directory, maintenance, queue, commands);
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
