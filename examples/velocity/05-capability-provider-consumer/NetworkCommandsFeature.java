package com.example.proxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "NetworkCommands", version = "1.0.0", enabledByDefault = true, requiresCapabilities = NetworkPlayerApi.class)
public final class NetworkCommandsFeature extends VelocityFeature<Object> {
    private NetworkPlayerApi players;

    public NetworkCommandsFeature(VelocityFeatureContext<Object> context) {
        super(context);
    }

    @Override
    public void initialize() {
        players = services().require(NetworkPlayerApi.class);
        logger().info("Network player API resolved");
    }

    @Override
    public void disable() {
        players = null;
    }
}
