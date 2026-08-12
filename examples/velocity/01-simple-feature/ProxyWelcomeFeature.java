package com.example.proxy.welcome;

import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

public final class ProxyWelcomeFeature extends VelocityFeature<Object, Void> {
    public ProxyWelcomeFeature(VelocityFeatureContext<Object, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Proxy welcome enabled on " + getContext().proxy().getVersion());
    }

    @Override
    public void disable() {
    }
}
