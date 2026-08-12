package com.example.myplugin;

import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class GreetingConsumerFeature extends PaperFeature<Plugin, Void> {
    public GreetingConsumerFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        GreetingApi greetings = requireCapability(GreetingApi.class);
        logger().info("Greeting API ready: " + greetings.getClass().getSimpleName());
    }

    @Override
    public void disable() {
    }
}
