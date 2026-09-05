package com.example.myplugin;

import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "GreetingProvider", version = "1.0.0", enabledByDefault = true, providesCapabilities = GreetingApi.class)
public final class GreetingProviderFeature extends PaperFeature<Plugin> {
    public GreetingProviderFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override
    public void initialize() {
        GreetingApi greetings = playerId -> "Welcome, " + playerId;
        services().publish(GreetingApi.class, greetings);
    }

    @Override
    public void disable() {
    }
}
