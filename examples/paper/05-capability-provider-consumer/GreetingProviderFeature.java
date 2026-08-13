package com.example.myplugin;

import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class GreetingProviderFeature extends PaperFeature<Plugin, Void> {
    public GreetingProviderFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override
    public void initialize() {
        GreetingApi greetings = playerId -> "Welcome, " + playerId;
        getContext().services().registerService(GreetingApi.class, greetings);
    }

    @Override
    public void disable() {
    }
}
