package com.example.myplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class ChatFeature extends PaperFeature<Plugin, Void> {
    public ChatFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        logger().info("Chat started after its required Profiles feature");
        findInternalService(Object.class); // Optional lookups are allowed; real code would use a meaningful contract.
    }

    @Override public void disable() { }
}
