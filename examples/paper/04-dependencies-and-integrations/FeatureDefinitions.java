package com.example.myplugin;

import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class FeatureDefinitions {
    private FeatureDefinitions() {
    }

    public static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> chat() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Chat", "1.0.0", ChatFeature.class, ChatFeature::new)
                .requiresFeatures("Profiles")
                .optionallyUsesFeatures("DiscordBridge")
                .enabledByDefault()
                .build();
    }

    public static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> placeholders() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Placeholders", "1.0.0", PlaceholderFeature.class, PlaceholderFeature::new)
                .requiresPlugins("PlaceholderAPI")
                .startupOrder(100)
                .enabledByDefault()
                .build();
    }
}
