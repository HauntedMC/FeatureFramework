package com.example.proxy;

import com.example.proxy.welcome.ProxyWelcomeFeature;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.host.FeatureCollection;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeature;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureContext;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;

import java.nio.file.Path;

public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHost featureHost;

    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void start() {
        FeatureDefinition<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>> welcome =
                FeatureDefinition.<VelocityFeature<Object, Void>, VelocityFeatureContext<Object, Void>>builder(
                                "Welcome", "1.0.0", ProxyWelcomeFeature.class, ProxyWelcomeFeature::new)
                        .enabledByDefault()
                        .build();

        featureHost = VelocityFeatureHost.builder(
                this, proxy, logger, dataDirectory, ProxyPlugin.class, FeatureCollection.of(welcome)).build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
