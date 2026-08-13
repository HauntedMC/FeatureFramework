package com.example.contracts;

import com.example.contracts.catalog.BuiltInFeatures;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.host.PaperDataProviderFeature;
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
        generatedClassName = "com.example.contracts.catalog.BuiltInFeatures",
        featurePackage = "com.example.contracts",
        featureBase = PaperDataProviderFeature.class,
        featureContext = PaperFeatureContext.class
)
public final class NetworkPlugin extends JavaPlugin {
    private PaperFeatureHostComposition<
            NetworkPlugin,
            String,
            PaperDataProviderFeature<NetworkPlugin>,
            FeatureDataManager> composition;

    @Override
    public void onEnable() {
        FrameworkLogger logger = FrameworkLogger.from(getLogger());
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>(getName(), capabilities);
        ConfigService configService = new ConfigService(
                getDataFolder().toPath(), logger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        PaperLocalization localization = new PaperLocalization(this, configService, player -> Language.EN);

        BrigadierDispatcher dispatcher = new BrigadierDispatcher(this, logger);
        dispatcher.resolveDispatcher();
        PaperFeatureResourcesFactory<FeatureDataManager> resources =
                PaperFeatureResourcesFactory.withDataProvider(
                        this,
                        getDataFolder().toPath(),
                        dispatcher,
                        () -> false,
                        logger,
                        () -> getServer().getServicesManager().getRegistration(DataProviderAPI.class) != null,
                        () -> "validate"
                );

        composition = PaperFeatureHostComposition.builder(
                        this,
                        getPluginMeta().getVersion(),
                        "contractnetwork",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        BuiltInFeatures.collection(),
                        logger)
                .hostName(getName())
                .build();
        composition.host().start();
    }

    @Override
    public void onDisable() {
        if (composition != null) composition.host().stop();
    }
}
