package com.example.myplugin.activity;

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
        var activity = FeatureDefinition
                .<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "Activity", "1.0.0", ActivityFeature.class, ActivityFeature::new)
                .enabledByDefault()
                .build();

        featureHost = PaperFeatureHost.builder(
                this,
                MyPlugin.class,
                FeatureCollection.of(activity)
        ).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
