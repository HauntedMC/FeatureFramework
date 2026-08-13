package com.example.myplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(name = "DiscordBridge", version = "1.0.0", enabledByDefault = true)
public final class DiscordBridgeFeature extends PaperFeature<Plugin, Void> {
    public DiscordBridgeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Discord bridge ready"); }
    @Override public void disable() { }
}
