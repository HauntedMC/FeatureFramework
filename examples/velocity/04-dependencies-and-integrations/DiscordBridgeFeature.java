package com.example.proxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,name = "DiscordBridge", version = "1.0.0", enabledByDefault = true)
public final class DiscordBridgeFeature extends VelocityFeature<Object> {
    public DiscordBridgeFeature(VelocityFeatureContext<Object> context) { super(context); }
    @Override public void initialize() { logger().info("Discord bridge ready"); }
    @Override public void disable() { }
}
