package com.example.myplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,name = "Profiles", version = "1.0.0", enabledByDefault = true)
public final class ProfilesFeature extends PaperFeature<Plugin> {
    public ProfilesFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override public void initialize() { logger().info("Profiles ready"); }
    @Override public void disable() { }
}
