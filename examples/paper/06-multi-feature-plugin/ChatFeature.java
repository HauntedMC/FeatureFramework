package com.example.largeplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class ChatFeature extends PaperFeature<Plugin, Void> {
    private PlayerProfileApi profiles;

    public ChatFeature(PaperFeatureContext<Plugin, Void> context) {
        super(context);
    }

    @Override public void initialize() { profiles = requireCapability(PlayerProfileApi.class); }
    @Override public void disable() { profiles = null; }
}
