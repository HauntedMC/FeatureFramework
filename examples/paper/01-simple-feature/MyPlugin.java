package com.example.myplugin;

import com.example.myplugin.welcome.WelcomeFeature;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost featureHost;

    @Override
    public void onEnable() {
        FeatureDefinition<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>> welcome =
                FeatureDefinition.<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                                "Welcome", "1.0.0", WelcomeFeature.class, WelcomeFeature::new)
                        .enabledByDefault()
                        .build();

        var features = FeatureCollection.of(welcome);
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, features).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
