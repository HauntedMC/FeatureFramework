package com.example.largeplugin;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class Features {
    private Features() { }

    public static FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> all() {
        var profiles = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Profiles", "1.0.0", ProfilesFeature.class, ProfilesFeature::new)
                .providesCapabilities(PlayerProfileApi.class)
                .enabledByDefault()
                .build();

        var chat = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Chat", "1.0.0", ChatFeature.class, ChatFeature::new)
                .requiresCapabilities(PlayerProfileApi.class)
                .enabledByDefault()
                .build();

        var moderation = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Moderation", "1.0.0", ModerationFeature.class, ModerationFeature::new)
                .requiresCapabilities(PlayerProfileApi.class)
                .enabledByDefault()
                .build();

        var placeholders = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "PlaceholderBridge", "1.0.0", PlaceholderBridgeFeature.class, PlaceholderBridgeFeature::new)
                .requiresPlugins("PlaceholderAPI")
                .optionallyUsesCapabilities(PlayerProfileApi.class)
                .enabledByDefault()
                .build();

        return FeatureCollection.of(profiles, chat, moderation, placeholders);
    }
}
