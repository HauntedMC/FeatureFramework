package com.example.myplugin;

import com.example.myplugin.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.plugin.java.JavaPlugin;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.myplugin.catalog.BuiltInFeatures",
        featurePackage = "com.example.myplugin")
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost<MyPlugin, String> featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection()).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
