package com.example.proxy;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class QueueFeature extends VelocityFeature<Object, Void> {
    public QueueFeature(VelocityFeatureContext<Object, Void> context) { super(context); }
    @Override public void initialize() { logger().info("Queue ready after ServerDirectory"); }
    @Override public void disable() { }
}
