package com.example.myplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class ProfilesFeature extends PaperFeature<Plugin, Void> {
    public ProfilesFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Profiles ready"); }
    @Override public void disable() { }
}
