package com.example.proxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class DiscordBridgeFeature extends VelocityFeature<Object, Void> {
    public DiscordBridgeFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("Discord bridge ready"); }
    @Override public void disable() { }
}
