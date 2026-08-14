package com.example.rollouts;

import com.example.rollouts.catalog.BuiltInFeatures;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.host.VelocityFeatureHost;
import nl.hauntedmc.featureframework.velocity.integration.dataprovider.VelocityDataProviderApiResolver;
import nl.hauntedmc.featureframework.velocity.integration.dataprovider.VelocityDataProviderContributor;

import java.nio.file.Path;

@Plugin(
        id = "rolloutrouter",
        name = "RolloutRouter",
        version = "1.0.0",
        dependencies = @Dependency(id = "dataprovider")
)
@GenerateFeatureCatalog(
        generatedClassName = "com.example.rollouts.catalog.BuiltInFeatures",
        featurePackage = "com.example.rollouts"
)
public final class RolloutProxyPlugin {
    private final ProxyServer proxy;
    private final ComponentLogger platformLogger;
    private final Path dataDirectory;
    private VelocityFeatureHost<RolloutProxyPlugin, String> featureHost;

    @Inject
    public RolloutProxyPlugin(
            ProxyServer proxy,
            ComponentLogger platformLogger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.platformLogger = platformLogger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        FrameworkLogger logger = FrameworkLogger.from(platformLogger);
        featureHost = VelocityFeatureHost.builder(
                        this, proxy, platformLogger, dataDirectory,
                        RolloutProxyPlugin.class, BuiltInFeatures.collection())
                .hostName("RolloutRouter")
                .capabilityNamespace("rolloutrouter")
                .contribute(VelocityDataProviderContributor.create(
                        this,
                        VelocityDataProviderApiResolver.supplier(
                                proxy::getPluginManager, platformLogger::warn),
                        logger,
                        () -> "validate"))
                .build();
        featureHost.start();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (featureHost != null) featureHost.stop();
    }
}
