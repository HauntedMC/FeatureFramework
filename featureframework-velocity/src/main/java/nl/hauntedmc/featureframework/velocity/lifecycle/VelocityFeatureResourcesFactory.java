package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceFactoryCore;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.velocity.command.CommandOwnershipRegistry;
import nl.hauntedmc.featureframework.velocity.integration.dataprovider.VelocityDataProviderApiResolver;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Assembles Velocity-native resources around the shared feature resource core. */
public final class VelocityFeatureResourcesFactory<D> {
    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger platformLogger;
    private final FeatureResourceFactoryCore<D> core;
    private final Consumer<? super D> dataManagerQuiesce;
    private final Consumer<? super D> dataManagerCleanup;
    private final CommandOwnershipRegistry commandOwnership = new CommandOwnershipRegistry();

    private VelocityFeatureResourcesFactory(
            Object plugin,
            ProxyServer proxy,
            Logger platformLogger,
            Path dataDirectory,
            FrameworkLogger logger,
            Supplier<? extends D> dataManagerFactory,
            BiConsumer<? super D, String> dataManagerBinder,
            Consumer<? super D> dataManagerQuiesce,
            Consumer<? super D> dataManagerCleanup
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.platformLogger = Objects.requireNonNull(platformLogger, "platformLogger");
        this.core = new FeatureResourceFactoryCore<>(
                dataDirectory, logger, dataManagerFactory, dataManagerBinder);
        this.dataManagerQuiesce = Objects.requireNonNull(dataManagerQuiesce, "dataManagerQuiesce");
        this.dataManagerCleanup = Objects.requireNonNull(dataManagerCleanup, "dataManagerCleanup");
    }

    public static VelocityFeatureResourcesFactory<FeatureDataManager> withDataProvider(
            Object plugin,
            ProxyServer proxy,
            Logger platformLogger,
            Path dataDirectory,
            FrameworkLogger logger,
            Supplier<String> schemaMode
    ) {
        Objects.requireNonNull(schemaMode, "schemaMode");
        return new VelocityFeatureResourcesFactory<>(
                plugin,
                proxy,
                platformLogger,
                dataDirectory,
                logger,
                () -> new FeatureDataManager(
                        plugin,
                        VelocityDataProviderApiResolver.supplier(proxy::getPluginManager, logger::warn),
                        logger,
                        schemaMode),
                FeatureDataManager::bindToFeature,
                FeatureDataManager::quiesce,
                FeatureDataManager::closeAllDataResources
        );
    }

    public static VelocityFeatureResourcesFactory<Void> withoutDataProvider(
            Object plugin,
            ProxyServer proxy,
            Logger platformLogger,
            Path dataDirectory,
            FrameworkLogger logger
    ) {
        return new VelocityFeatureResourcesFactory<>(
                plugin,
                proxy,
                platformLogger,
                dataDirectory,
                logger,
                () -> null,
                (ignored, featureName) -> { },
                ignored -> { },
                ignored -> { }
        );
    }

    public VelocityFeatureResources<D> create(
            String featureName,
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        FeatureResourceFactoryCore.Bundle<D> common = core.create(featureName, capabilities, internalServices);
        D dataManager = common.dataManager();
        return new VelocityFeatureResources<>(
                new FeatureTaskManager(proxy.getScheduler(), plugin),
                new FeatureCommandManager(
                        plugin, proxy.getCommandManager(), commandOwnership, platformLogger, common.featureName()),
                new FeatureListenerManager(plugin, proxy.getEventManager()),
                dataManager,
                dataManager == null ? () -> { } : () -> dataManagerQuiesce.accept(dataManager),
                dataManager == null ? () -> { } : () -> dataManagerCleanup.accept(dataManager),
                common.cacheManager(),
                common.serviceManager()
        );
    }

    public CommandOwnershipRegistry commandOwnership() { return commandOwnership; }
}
