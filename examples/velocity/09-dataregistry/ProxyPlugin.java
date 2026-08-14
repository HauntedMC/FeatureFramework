package com.example.registryproxy;

import com.example.registryproxy.catalog.BuiltInFeatures;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.velocity.integration.dataregistry.VelocityDataRegistryPluginDiscovery;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;
import nl.hauntedmc.featureframework.velocity.integration.dataregistry.VelocityDataRegistryContributor;

import java.nio.file.Path;

@GenerateFeatureCatalog(
        generatedClassName = "com.example.registryproxy.catalog.BuiltInFeatures",
        featurePackage = "com.example.registryproxy")
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
        featureHost = VelocityFeatureHost.builder(
                        this, proxy, logger, dataDirectory, "1.0.0", ProxyPlugin.class,
                        BuiltInFeatures.collection())
                .hostName("ExampleRegistryProxy")
                .capabilityNamespace("exampleregistryproxy")
                .contribute(VelocityDataRegistryContributor.create(
                        VelocityDataRegistryPluginDiscovery.supplier(proxy, "dataregistry")))
                .build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
