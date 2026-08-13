package com.example.largeproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        name = "NetworkCommands", version = "1.0.0", enabledByDefault = true,
        requiresCapabilities = ServerDirectoryApi.class, optionallyUsesFeatures = "Maintenance")
public final class NetworkCommandsFeature extends VelocityFeature<Object, Void> {
    private ServerDirectoryApi directory;

    public NetworkCommandsFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { directory = requireCapability(ServerDirectoryApi.class); }
    @Override public void disable() { directory = null; }
}
