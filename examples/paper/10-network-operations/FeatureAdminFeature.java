package com.example.networkops.paper;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;

/** Owns the operator command, while the host remains the lifecycle authority. */
@FeatureDeclaration(
        scope = FeatureScope.NETWORK,name = "FeatureAdmin", version = "1.0.0", enabledByDefault = true)
public final class FeatureAdminFeature extends PaperFeature<MyPlugin> {
    public FeatureAdminFeature(PaperFeatureContext<MyPlugin> context) {
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
