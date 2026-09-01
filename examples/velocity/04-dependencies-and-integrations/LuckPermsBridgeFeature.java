package com.example.proxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "LuckPermsBridge", version = "1.0.0", enabledByDefault = true, requiresPlugins = "luckperms")
public final class LuckPermsBridgeFeature extends VelocityFeature<Object> {
    public LuckPermsBridgeFeature(VelocityFeatureContext<Object> context) { super(context); }
    @Override public void initialize() { logger().info("LuckPerms bridge ready"); }
    @Override public void disable() { }
}
