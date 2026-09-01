package com.example.largeproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "NetworkCommands", version = "1.0.0", enabledByDefault = true,
        requiresCapabilities = ServerDirectoryApi.class, optionallyUsesFeatures = "Maintenance")
public final class NetworkCommandsFeature extends VelocityFeature<Object> {
    private ServerDirectoryApi directory;

    public NetworkCommandsFeature(VelocityFeatureContext<Object> context) { super(context); }
    @Override public void initialize() { directory = requireCapability(ServerDirectoryApi.class); }
    @Override public void disable() { directory = null; }
}
