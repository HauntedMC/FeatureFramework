package com.example.myplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        name = "Chat", version = "1.0.0", enabledByDefault = true,
        requiresFeatures = "Profiles", optionallyUsesFeatures = "DiscordBridge")
public final class ChatFeature extends PaperFeature<Plugin> {
    public ChatFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Chat started after its required Profiles feature");
        findInternalService(Object.class); // Optional lookups are allowed; real code would use a meaningful contract.
    }

    @Override public void disable() { }
}
