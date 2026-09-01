package com.example.myplugin.welcome;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,name = "Welcome", version = "1.0.0", enabledByDefault = true)
public final class WelcomeFeature extends PaperFeature<Plugin> {
    public WelcomeFeature(PaperFeatureContext<Plugin> context) {
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
