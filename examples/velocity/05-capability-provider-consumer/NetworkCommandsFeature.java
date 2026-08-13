package com.example.proxy;

import com.example.network.api.NetworkPlayerApi;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class NetworkCommandsFeature extends VelocityFeature<Object, Void> {
    private NetworkPlayerApi players;

    public NetworkCommandsFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        players = requireCapability(NetworkPlayerApi.class);
        logger().info("Network player API resolved");
    }

    @Override
    public void disable() {
        players = null;
    }
}
