package com.example.proxy.welcome;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(name = "Welcome", version = "1.0.0", enabledByDefault = true)
public final class ProxyWelcomeFeature extends VelocityFeature<Object> {
    public ProxyWelcomeFeature(VelocityFeatureContext<Object> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Proxy welcome enabled on " + context().proxy().getVersion());
    }

    @Override
    public void disable() {
    }
}
