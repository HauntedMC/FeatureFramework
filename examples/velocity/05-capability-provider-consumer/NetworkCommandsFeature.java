package com.example.proxy;

import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkCommandsFeature extends VelocityFeature<Object, Void> {
    public NetworkCommandsFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        NetworkPlayerApi players = requireCapability(NetworkPlayerApi.class);
        logger().info("Network player API available: " + players.getClass().getSimpleName());
    }

    @Override
    public void disable() {
    }
}
