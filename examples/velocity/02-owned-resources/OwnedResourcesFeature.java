package com.example.proxy.activity;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class OwnedResourcesFeature extends VelocityFeature<Object, Void> {
    public OwnedResourcesFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        var tasks = resources().getTaskManager();
        var listeners = resources().getListenerManager();
        logger().info("Owned task manager: " + tasks.getClass().getSimpleName());
        logger().info("Owned listener manager: " + listeners.getClass().getSimpleName());
    }

    @Override
    public void disable() {
    }
}
