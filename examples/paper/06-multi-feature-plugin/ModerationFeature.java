package com.example.largeplugin;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

@FeatureDeclaration(
        scope = FeatureScope.NODE,
        name = "Moderation", version = "1.0.0", enabledByDefault = true, requiresCapabilities = PlayerProfileApi.class)
public final class ModerationFeature extends PaperFeature<Plugin> {
    private PlayerProfileApi profiles;

    public ModerationFeature(PaperFeatureContext<Plugin> context) {
        super(context);
    }

    @Override public void initialize() { profiles = services().require(PlayerProfileApi.class); }
    @Override public void disable() { profiles = null; }
}
