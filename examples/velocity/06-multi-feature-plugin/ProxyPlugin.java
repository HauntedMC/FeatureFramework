package com.example.largeproxy;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
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
        featureHost = VelocityFeatureHost.builder(
                        this, proxy, logger, dataDirectory, ProxyPlugin.class, Features.all())
                .hostName("ExampleNetwork")
                .version("1.0.0")
                .capabilityNamespace("examplenetwork")
                .build();
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
