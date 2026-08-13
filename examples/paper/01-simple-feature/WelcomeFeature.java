package com.example.myplugin.welcome;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class WelcomeFeature extends PaperFeature<Plugin, Void> {
    public WelcomeFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Welcome enabled");
    }

    @Override
    public void disable() {
    }
}
