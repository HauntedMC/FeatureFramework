package com.example.myplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "GreetingConsumer", version = "1.0.0", enabledByDefault = true, requiresCapabilities = GreetingApi.class)
public final class GreetingConsumerFeature extends PaperFeature<Plugin> {
    private GreetingApi greetings;

    public GreetingConsumerFeature(PaperFeatureContext<Plugin> context) {
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
