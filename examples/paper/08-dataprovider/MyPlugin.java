package com.example.dataplugin;

import com.example.dataplugin.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHost;
import nl.hauntedmc.featureframework.paper.integration.dataprovider.PaperDataProviderApiResolver;
import nl.hauntedmc.featureframework.paper.integration.dataprovider.PaperDataProviderContributor;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.java.JavaPlugin;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.dataplugin.catalog.BuiltInFeatures",
        featurePackage = "com.example.dataplugin")
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHost<MyPlugin, String> featureHost;

    @Override
    public void onEnable() {
        FrameworkLogger logger = FrameworkLogger.from(getLogger());
        featureHost = PaperFeatureHost.builder(this, MyPlugin.class, BuiltInFeatures.collection())
                .capabilityNamespace("exampledata")
                .contribute(PaperDataProviderContributor.create(
                        this,
                        PaperDataProviderApiResolver.supplier(
                                () -> getServer().getServicesManager(), getLogger()::warning),
                        logger,
                        () -> "validate"))
                .build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
