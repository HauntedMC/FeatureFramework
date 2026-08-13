package com.example.lifecycle;

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
        var remoteSync = FeatureDefinition
                .<PaperFeature<Plugin, Void>, PaperFeatureContext<Plugin, Void>>builder(
                        "RemoteSync", "1.0.0", RemoteSyncFeature.class, RemoteSyncFeature::new)
                .enabledByDefault()
                .build();
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, FeatureCollection.of(remoteSync)).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
