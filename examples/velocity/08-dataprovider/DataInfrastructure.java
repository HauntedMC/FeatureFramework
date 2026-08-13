package com.example.velocitydata;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.config.DefaultFeatureConfiguration;
import nl.hauntedmc.featureframework.host.FeatureCollection;
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

public final class DataInfrastructure {
    private DataInfrastructure() {
    }

    public static VelocityFeatureHostComposition<?, ?, ?, ?> create(
            ProxyPlugin plugin, ProxyServer proxy, ComponentLogger platformLogger, Path dataDirectory) {
        FrameworkLogger logger = FrameworkLogger.from(platformLogger);
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                ProxyPlugin.class.getPackageName(), ProxyPlugin.class.getClassLoader());
        FeatureRuntime<FeatureId, DefaultCapabilityRegistry> runtime =
                new FeatureRuntime<>("ExampleDataProxy", capabilities);
        ConfigService configService = new ConfigService(
                dataDirectory, logger, ProxyPlugin.class.getClassLoader());
        DefaultFeatureConfiguration configuration = new DefaultFeatureConfiguration(configService, logger);
        VelocityLocalization localization = new VelocityLocalization(
                platformLogger, ProxyPlugin.class.getClassLoader(), configService, player -> Language.EN);
        VelocityFeatureResourcesFactory<FeatureDataManager> resources =
                VelocityFeatureResourcesFactory.withDataProvider(
                        plugin, proxy, platformLogger, dataDirectory, logger, () -> "validate");
        FeatureCollection<VelocityFeature<ProxyPlugin, FeatureDataManager>, VelocityFeatureContext<ProxyPlugin, FeatureDataManager>> features =
                DataFeatures.all();

        return VelocityFeatureHostComposition.builder(
                        plugin, proxy, platformLogger, "1.0.0", "exampledataproxy",
                        runtime, configuration, localization, resources::create, features, logger)
                .hostName("ExampleDataProxy")
                .build();
    }
}
