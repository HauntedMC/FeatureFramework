package com.example.rollouts;

import com.example.rollouts.catalog.BuiltInFeatures;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityDataProviderFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHostComposition;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResourcesFactory;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;

import java.nio.file.Path;

@Plugin(
        id = "rolloutrouter",
        name = "RolloutRouter",
        version = "1.0.0",
        dependencies = @Dependency(id = "dataprovider")
)
@GenerateFeatureCatalog(
        generatedClassName = "com.example.rollouts.catalog.BuiltInFeatures",
        featurePackage = "com.example.rollouts",
        featureBase = VelocityDataProviderFeature.class,
        featureContext = VelocityFeatureContext.class
)
public final class RolloutProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger platformLogger;
    private final Path dataDirectory;
    private VelocityFeatureHostComposition<
            RolloutProxyPlugin,
            String,
            VelocityDataProviderFeature<RolloutProxyPlugin>,
            FeatureDataManager> composition;

    @Inject
    public RolloutProxyPlugin(
            ProxyServer proxy,
            ComponentLogger platformLogger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.platformLogger = platformLogger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        FrameworkLogger logger = FrameworkLogger.from(platformLogger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>("RolloutRouter", capabilities);
        ConfigService configService = new ConfigService(dataDirectory, logger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        VelocityLocalization localization = new VelocityLocalization(
                platformLogger,
                getClass().getClassLoader(),
                configService,
                player -> Language.EN
        );
        VelocityFeatureResourcesFactory<FeatureDataManager> resources =
                VelocityFeatureResourcesFactory.withDataProvider(
                        this, proxy, platformLogger, dataDirectory, logger, () -> "validate");

        composition = VelocityFeatureHostComposition.builder(
                        this,
                        proxy,
                        platformLogger,
                        "1.0.0",
                        "rolloutrouter",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        BuiltInFeatures.collection(),
                        logger)
                .hostName("RolloutRouter")
                .build();
        composition.host().start();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (composition != null) composition.host().stop();
    }
}
