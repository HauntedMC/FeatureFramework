package com.example.myplugin;

import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class FeatureDefinitions {
    private FeatureDefinitions() { }

    public static FeatureCollection<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> all() {
        return FeatureCollection.of(
                simple("Profiles", ProfilesFeature.class, ProfilesFeature::new),
                simple("DiscordBridge", DiscordBridgeFeature.class, DiscordBridgeFeature::new),
                chat(),
                placeholders()
        );
    }

    private static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> chat() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Chat", "1.0.0", ChatFeature.class, ChatFeature::new)
                .requiresFeatures("Profiles")
                .optionallyUsesFeatures("DiscordBridge")
                .enabledByDefault()
                .build();
    }

    private static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> placeholders() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Placeholders", "1.0.0", PlaceholderFeature.class, PlaceholderFeature::new)
                .requiresPlugins("PlaceholderAPI")
                .startupOrder(100)
                .enabledByDefault()
                .build();
    }

    private static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> simple(
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
