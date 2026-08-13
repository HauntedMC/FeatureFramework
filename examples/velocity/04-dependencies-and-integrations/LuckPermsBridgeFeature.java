package com.example.proxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class LuckPermsBridgeFeature extends VelocityFeature<Object, Void> {
    public LuckPermsBridgeFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("LuckPerms bridge ready"); }
    @Override public void disable() { }
}
