package com.example.largeplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class PlaceholderBridgeFeature extends PaperFeature<Plugin, Void> {
    public PlaceholderBridgeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Placeholder bridge enabled"); }
    @Override public void disable() { }
}
