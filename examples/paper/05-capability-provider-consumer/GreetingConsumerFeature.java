package com.example.myplugin;

import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class GreetingConsumerFeature extends PaperFeature<Plugin, Void> {
    private GreetingApi greetings;

    public GreetingConsumerFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        greetings = requireCapability(GreetingApi.class);
        logger().info("Greeting API resolved");
    }

    @Override
    public void disable() {
        greetings = null;
    }
}
