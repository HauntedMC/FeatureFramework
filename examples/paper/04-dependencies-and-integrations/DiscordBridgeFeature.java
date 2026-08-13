package com.example.myplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class DiscordBridgeFeature extends PaperFeature<Plugin, Void> {
    public DiscordBridgeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Discord bridge ready"); }
    @Override public void disable() { }
}
