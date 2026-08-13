package com.example.paperdata;

import nl.hauntedmc.featureframework.paper.host.PaperFeatureHostComposition;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHostComposition<?, ?, ?, ?> featureHost;

    @Override
    public void onEnable() {
        featureHost = DataExampleHost.create(this);
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
