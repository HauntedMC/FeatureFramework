package com.example.registryplugin;

import com.example.registryplugin.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryPluginDiscovery;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import nl.hauntedmc.featureframework.paper.integration.dataregistry.PaperDataRegistryContributor;
import org.bukkit.plugin.java.JavaPlugin;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.registryplugin.catalog.BuiltInFeatures",
        featurePackage = "com.example.registryplugin")
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost<MyPlugin, String> featureHost;

    @Override
    public void onEnable() {
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection())
                .capabilityNamespace("exampleregistry")
                .contribute(PaperDataRegistryContributor.create(
                        PaperDataRegistryPluginDiscovery.supplier(this, "DataRegistry")))
                .build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
