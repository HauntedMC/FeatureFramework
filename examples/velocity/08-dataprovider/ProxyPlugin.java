package com.example.dataproxy;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.runtime.FeatureRuntime;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHostComposition;
import nl.hauntedmc.featureframework.velocity.lifecycle.VelocityFeatureResourcesFactory;
import nl.hauntedmc.featureframework.velocity.localization.VelocityLocalization;

import java.nio.file.Path;

public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHostComposition<
            ProxyPlugin,
            String,
            VelocityFeature<ProxyPlugin, FeatureDataManager>,
            FeatureDataManager> featureHost;

    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void start() {
        FrameworkLogger frameworkLogger = FrameworkLogger.from(logger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                getClass().getPackageName(), getClass().getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>("ExampleDataProxy", capabilities);

        ConfigService configService = new ConfigService(
                dataDirectory, frameworkLogger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration =
                new DefaultFeatureConfiguration(configService, frameworkLogger);
        VelocityLocalization localization = new VelocityLocalization(
                logger,
                getClass().getClassLoader(),
                configService,
                player -> Language.EN);

        VelocityFeatureResourcesFactory<FeatureDataManager> resources =
                VelocityFeatureResourcesFactory.withDataProvider(
                        this,
                        proxy,
                        logger,
                        dataDirectory,
                        frameworkLogger,
                        () -> "validate");

        FeatureDefinition<
                VelocityFeature<ProxyPlugin, FeatureDataManager>,
                VelocityFeatureContext<ProxyPlugin, FeatureDataManager>> storage =
                FeatureDefinition
                        .<VelocityFeature<ProxyPlugin, FeatureDataManager>,
                                VelocityFeatureContext<ProxyPlugin, FeatureDataManager>>builder(
                                "NetworkStorage",
                                "1.0.0",
                                NetworkStorageFeature.class,
                                NetworkStorageFeature::new)
                        .requiresPlugins("dataprovider")
                        .enabledByDefault()
                        .build();

        FeatureCollection<
                VelocityFeature<ProxyPlugin, FeatureDataManager>,
                VelocityFeatureContext<ProxyPlugin, FeatureDataManager>> features =
                FeatureCollection.of(storage);

        featureHost = VelocityFeatureHostComposition.builder(
                        this,
                        proxy,
                        logger,
                        "1.0.0",
                        "exampledataproxy",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        features,
                        frameworkLogger)
                .hostName("ExampleDataProxy")
                .build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
