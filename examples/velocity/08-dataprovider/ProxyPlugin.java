package com.example.dataproxy;

import com.example.dataproxy.catalog.BuiltInFeatures;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;
import nl.hauntedmc.featureframework.velocity.integration.dataprovider.VelocityDataProviderApiResolver;
import nl.hauntedmc.featureframework.velocity.integration.dataprovider.VelocityDataProviderContributor;

import java.nio.file.Path;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.dataproxy.catalog.BuiltInFeatures",
        featurePackage = "com.example.dataproxy")
public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHost<ProxyPlugin, String> featureHost;

    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void start() {
        FrameworkLogger frameworkLogger = FrameworkLogger.from(logger);
        featureHost = VelocityFeatureHost.builder(
                        this, proxy, logger, dataDirectory, "1.0.0", ProxyPlugin.class,
                        BuiltInFeatures.collection())
                .hostName("ExampleDataProxy")
                .capabilityNamespace("exampledataproxy")
                .contribute(VelocityDataProviderContributor.create(
                        this,
                        VelocityDataProviderApiResolver.supplier(proxy::getPluginManager, logger::warn),
                        frameworkLogger,
                        () -> "validate"))
                .build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
