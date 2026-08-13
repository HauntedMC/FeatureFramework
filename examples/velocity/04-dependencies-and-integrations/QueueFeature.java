package com.example.proxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        name = "Queue", version = "1.0.0", enabledByDefault = true,
        requiresFeatures = "ServerDirectory", optionallyUsesFeatures = "DiscordBridge")
public final class QueueFeature extends VelocityFeature<Object, Void> {
    public QueueFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("Queue ready after ServerDirectory"); }
    @Override public void disable() { }
}
