package com.example.largeplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        name = "PlaceholderBridge", version = "1.0.0", enabledByDefault = true,
        requiresPlugins = "PlaceholderAPI", optionallyUsesCapabilities = PlayerProfileApi.class)
public final class PlaceholderBridgeFeature extends PaperFeature<Plugin, Void> {
    public PlaceholderBridgeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Placeholder bridge enabled"); }
    @Override public void disable() { }
}
