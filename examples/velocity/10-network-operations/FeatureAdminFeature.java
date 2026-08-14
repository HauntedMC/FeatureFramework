package com.example.networkops.velocity;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;

@FeatureDeclaration(name = "FeatureAdmin", version = "1.0.0", enabledByDefault = true)
public final class FeatureAdminFeature extends VelocityFeature<ProxyPlugin> {
    public FeatureAdminFeature(VelocityFeatureContext<ProxyPlugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().commands().registerBrigadierCommand(new FeatureAdminCommand(plugin()));
    }

    @Override
    public void disable() {
        // The feature resource scope unregisters /features automatically.
    }
}
