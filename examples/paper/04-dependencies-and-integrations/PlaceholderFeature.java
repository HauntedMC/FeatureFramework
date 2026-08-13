package com.example.myplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class PlaceholderFeature extends PaperFeature<Plugin, Void> {
    public PlaceholderFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("PlaceholderAPI is available"); }
    @Override public void disable() { }
}
