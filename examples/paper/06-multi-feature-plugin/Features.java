package com.example.largeplugin;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class Features {
    private Features() {
    }

    public static FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> all() {
        var profiles = definition("Profiles", ProfilesFeature.class, ProfilesFeature::new);
        var chat = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Chat", "1.0.0", ChatFeature.class, ChatFeature::new)
                .requiresFeatures("Profiles")
                .enabledByDefault()
                .build();
        var moderation = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Moderation", "1.0.0", ModerationFeature.class, ModerationFeature::new)
                .requiresFeatures("Profiles")
                .enabledByDefault()
                .build();
        var placeholders = FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "PlaceholderBridge", "1.0.0", PlaceholderBridgeFeature.class, PlaceholderBridgeFeature::new)
                .requiresPlugins("PlaceholderAPI")
                .enabledByDefault()
                .build();
        return FeatureCollection.of(profiles, chat, moderation, placeholders);
    }

    private static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> definition(
            String name,
            Class<? extends PaperFeature<Plugin, Void>> type,
            java.util.function.Function<PaperFeatureContext<Plugin, Void>, ? extends PaperFeature<Plugin, Void>> factory
    ) {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        name, "1.0.0", type, factory)
                .enabledByDefault()
                .build();
    }
}
