package com.example.velocitydata;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHostComposition;

import java.nio.file.Path;

public final class DataFeatureHost {
    private DataFeatureHost() {
    }

    public static VelocityFeatureHostComposition<?, ?, ?, ?> create(
            ProxyPlugin plugin, ProxyServer proxy, ComponentLogger logger, Path dataDirectory) {
        return DataInfrastructure.create(plugin, proxy, logger, dataDirectory);
    }
}
