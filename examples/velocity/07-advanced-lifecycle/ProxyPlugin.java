package com.example.proxy.lifecycle;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;

import java.nio.file.Path;

@Plugin(id = "ff-example-lifecycle", name = "FeatureFrameworkLifecycleExample", version = "1.0.0")
public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHost featureHost;

    @Inject
    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        var sync = FeatureDefinition
                .<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                        "NetworkSync", "1.0.0", NetworkSyncFeature.class, NetworkSyncFeature::new)
                .enabledByDefault()
                .build();
        featureHost = VelocityFeatureHost.builder(
                this, proxy, logger, dataDirectory, ProxyPlugin.class, FeatureCollection.of(sync)
        ).build();
        featureHost.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (featureHost != null) featureHost.stop();
    }
}
