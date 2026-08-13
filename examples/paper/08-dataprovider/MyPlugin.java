package com.example.dataplugin;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import com.example.dataplugin.catalog.BuiltInFeatures;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.host.PaperFeature;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureContext;
import nl.hauntedmc.featureframework.paper.host.PaperFeatureHostComposition;
import nl.hauntedmc.featureframework.paper.lifecycle.PaperFeatureResourcesFactory;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.java.JavaPlugin;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.dataplugin.catalog.BuiltInFeatures",
        featurePackage = "com.example.dataplugin",
        featureBase = PaperFeature.class,
        featureContext = PaperFeatureContext.class)
public final class MyPlugin extends JavaPlugin {
    private PaperFeatureHostComposition<
            MyPlugin,
            String,
            PaperFeature<MyPlugin, FeatureDataManager>,
            FeatureDataManager> featureHost;

    @Override
    public void onEnable() {
        FrameworkLogger frameworkLogger = FrameworkLogger.from(getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(getName(), capabilities);

        ConfigService configService = new ConfigService(
                getDataFolder().toPath(), frameworkLogger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration =
                new DefaultFeatureConfiguration(configService, frameworkLogger);
        PaperLocalization localization =
                new PaperLocalization(this, configService, player -> Language.EN);

        BrigadierDispatcher dispatcher = new BrigadierDispatcher(this, frameworkLogger);
        dispatcher.resolveDispatcher();

        PaperFeatureResourcesFactory<FeatureDataManager> resources =
                PaperFeatureResourcesFactory.withDataProvider(
                        this,
                        getDataFolder().toPath(),
                        dispatcher,
                        () -> true,
                        frameworkLogger,
                        () -> getServer().getServicesManager().getRegistration(DataProviderAPI.class) != null,
                        () -> "validate"
                );

        featureHost = PaperFeatureHostComposition.builder(
                        this,
                        getPluginMeta().getVersion(),
                        "exampledata",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        BuiltInFeatures.collection(),
                        frameworkLogger)
                .hostName(getName())
                .build();
        featureHost.start();
    }

    @Override
    public void onDisable() {
        if (featureHost != null) featureHost.stop();
    }
}
