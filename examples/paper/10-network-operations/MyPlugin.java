package com.example.networkops.paper;

import com.example.networkops.paper.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.networkops.paper.catalog.BuiltInFeatures",
        featurePackage = "com.example.networkops.paper",
        featureBase = PaperFeature.class,
        featureContext = PaperFeatureContext.class)
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection()).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }

    public PaperFeatureHost featureHost() {
        return Objects.requireNonNull(featureHost, "Feature host has not started");
    }
}
