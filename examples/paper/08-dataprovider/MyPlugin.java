package com.example.dataplugin;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
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

        FeatureDefinition<
                PaperFeature<MyPlugin, FeatureDataManager>,
                PaperFeatureContext<MyPlugin, FeatureDataManager>> storage =
                FeatureDefinition
                        .<PaperFeature<MyPlugin, FeatureDataManager>,
                                PaperFeatureContext<MyPlugin, FeatureDataManager>>builder(
                                "PlayerStorage",
                                "1.0.0",
                                PlayerStorageFeature.class,
                                PlayerStorageFeature::new)
                        .requiresPlugins("DataProvider")
                        .enabledByDefault()
                        .build();

        FeatureCollection<
                PaperFeature<MyPlugin, FeatureDataManager>,
                PaperFeatureContext<MyPlugin, FeatureDataManager>> features =
                FeatureCollection.of(storage);

        featureHost = PaperFeatureHostComposition.builder(
                        this,
                        getPluginMeta().getVersion(),
                        "exampledata",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        features,
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
