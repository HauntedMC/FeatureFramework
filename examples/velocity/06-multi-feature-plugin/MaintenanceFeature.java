package com.example.largeproxy;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(
        scope = FeatureScope.NODE,name = "Maintenance", version = "1.0.0", enabledByDefault = true)
public final class MaintenanceFeature extends VelocityFeature<Object> {
    public MaintenanceFeature(VelocityFeatureContext<Object> context) { super(context); }
    @Override public void initialize() { logger().info("Maintenance feature ready"); }
    @Override public void disable() { }
}
