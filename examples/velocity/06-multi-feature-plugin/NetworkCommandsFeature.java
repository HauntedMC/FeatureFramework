package com.example.largeproxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkCommandsFeature extends VelocityFeature<Object, Void> {
    private ServerDirectoryApi directory;

    public NetworkCommandsFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { directory = requireCapability(ServerDirectoryApi.class); }
    @Override public void disable() { directory = null; }
}
