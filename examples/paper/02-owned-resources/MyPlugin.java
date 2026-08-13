package com.example.myplugin.activity;

import com.example.myplugin.activity.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.plugin.java.JavaPlugin;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.myplugin.activity.catalog.BuiltInFeatures",
        featurePackage = "com.example.myplugin.activity",
        featureBase = PaperFeature.class,
        featureContext = PaperFeatureContext.class)
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(
                this,
                MyPlugin.class,
                BuiltInFeatures.collection()
        ).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
