package com.example.largeplugin;

import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, Features.all()).build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
