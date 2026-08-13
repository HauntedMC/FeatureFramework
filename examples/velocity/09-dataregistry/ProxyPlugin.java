package com.example.registryproxy;

import com.velocitypowered.api.proxy.ProxyServer;
import com.example.registryproxy.catalog.BuiltInFeatures;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
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

@GenerateFeatureCatalog(
        generatedClassName = "com.example.registryproxy.catalog.BuiltInFeatures",
        featurePackage = "com.example.registryproxy",
        featureBase = VelocityFeature.class,
        featureContext = VelocityFeatureContext.class)
public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHostComposition<
            ProxyPlugin,
            String,
            VelocityFeature<ProxyPlugin, Void>,
            Void> featureHost;

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
                new FeatureRuntime<>("ExampleRegistryProxy", capabilities);

        ConfigService configService = new ConfigService(
                dataDirectory, frameworkLogger, getClass().getClassLoader());
        DefaultFeatureConfiguration configuration =
                new DefaultFeatureConfiguration(configService, frameworkLogger);
        VelocityLocalization localization = new VelocityLocalization(
                logger,
                getClass().getClassLoader(),
                configService,
                player -> Language.EN);
        VelocityFeatureResourcesFactory<Void> resources =
                VelocityFeatureResourcesFactory.withoutDataProvider(
                        this,
                        proxy,
                        logger,
                        dataDirectory,
                        frameworkLogger);

        featureHost = VelocityFeatureHostComposition.builder(
                        this,
                        proxy,
                        logger,
                        "1.0.0",
                        "exampleregistryproxy",
                        runtime,
                        configuration,
                        localization,
                        resources::create,
                        BuiltInFeatures.collection(),
                        frameworkLogger)
                .hostName("ExampleRegistryProxy")
                .dataRegistryPlugin("dataregistry")
                .build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
