package com.example.proxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class ServerDirectoryFeature extends VelocityFeature<Object, Void> {
    public ServerDirectoryFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("Server directory ready"); }
    @Override public void disable() { }
}
