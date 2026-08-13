package com.example.largeproxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class MaintenanceFeature extends VelocityFeature<Object, Void> {
    public MaintenanceFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("Maintenance feature ready"); }
    @Override public void disable() { }
}
