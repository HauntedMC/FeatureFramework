package com.example.velocitydata;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHostComposition;

import java.nio.file.Path;

public final class ProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private VelocityFeatureHostComposition<?, ?, ?, ?> featureHost;

    public ProxyPlugin(ProxyServer proxy, ComponentLogger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void start() {
        featureHost = DataFeatureHost.create(this, proxy, logger, dataDirectory);
        featureHost.start();
    }

    public void stop() {
        if (featureHost != null) featureHost.stop();
    }
}
