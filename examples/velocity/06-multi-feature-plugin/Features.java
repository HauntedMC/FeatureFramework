package com.example.largeproxy;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class Features {
    private Features() { }

    public static FeatureCollection<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> all() {
        var directory = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "ServerDirectory", "1.0.0", ServerDirectoryFeature.class, ServerDirectoryFeature::new)
                .providesCapabilities(ServerDirectoryApi.class)
                .enabledByDefault()
                .build();

        var maintenance = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "Maintenance", "1.0.0", MaintenanceFeature.class, MaintenanceFeature::new)
                .enabledByDefault()
                .build();

        var queue = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "Queue", "1.0.0", QueueFeature.class, QueueFeature::new)
                .requiresCapabilities(ServerDirectoryApi.class)
                .enabledByDefault()
                .build();

        var commands = FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "NetworkCommands", "1.0.0", NetworkCommandsFeature.class, NetworkCommandsFeature::new)
                .requiresCapabilities(ServerDirectoryApi.class)
                .optionallyUsesFeatures("Maintenance")
                .enabledByDefault()
                .build();

        return FeatureCollection.of(directory, maintenance, queue, commands);
    }
}
