package com.example.myplugin;

import com.example.myplugin.api.GreetingApi;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import org.bukkit.plugin.Plugin;

public final class Definitions {
    private Definitions() {
    }

    public static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> provider() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "GreetingProvider", "1.0.0", GreetingProviderFeature.class, GreetingProviderFeature::new)
                .providesCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
    }

    public static FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> consumer() {
        return FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "GreetingConsumer", "1.0.0", GreetingConsumerFeature.class, GreetingConsumerFeature::new)
                .requiresCapabilities(GreetingApi.class)
                .enabledByDefault()
                .build();
    }
}
