package com.example.networkops.velocity;

import com.example.networkops.velocity.catalog.BuiltInFeatures;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;

import java.nio.file.Path;
import java.util.Objects;

@Plugin(id = "ff-example-network-operations", name = "FeatureFrameworkNetworkOperations", version = "1.0.0")
@GenerateFeatureCatalog(
        generatedClassName = "com.example.networkops.velocity.catalog.BuiltInFeatures",
        featurePackage = "com.example.networkops.velocity")
public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHost<ProxyPlugin, String> featureHost;

    @Inject
    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        featureHost = VelocityFeatureHost.builder(
                this, proxy, logger, dataDirectory, ProxyPlugin.class, BuiltInFeatures.collection()).build();
        featureHost.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (featureHost != null) featureHost.stop();
    }

    public VelocityFeatureHost<ProxyPlugin, String> featureHost() {
        return Objects.requireNonNull(featureHost, "Feature host has not started");
    }
}
