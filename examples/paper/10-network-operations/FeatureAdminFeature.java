package com.example.networkops.paper;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;

/** Owns the operator command, while the host remains the lifecycle authority. */
@FeatureDeclaration(name = "FeatureAdmin", version = "1.0.0", enabledByDefault = true)
public final class FeatureAdminFeature extends PaperFeature<MyPlugin, Void> {
    public FeatureAdminFeature(PaperFeatureContext<MyPlugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        resources().getCommandManager().registerBrigadierCommand(new FeatureAdminCommand(plugin()));
    }

    @Override
    public void disable() {
        // The feature resource scope unregisters /features automatically.
    }
}
