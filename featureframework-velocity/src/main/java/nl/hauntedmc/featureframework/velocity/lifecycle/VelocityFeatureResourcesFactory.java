package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
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

/**
 * Assembles the standard framework-owned resource scope for a Velocity feature.
 *
 * <p>Callers choose framework-owned DataProvider discovery or a no-data scope. Scheduler, command,
 * listener, cache, and service ownership are constructed consistently here.</p>
 */
public final class VelocityFeatureResourcesFactory<D> {
    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger platformLogger;
    private final Path dataDirectory;
    private final FrameworkLogger logger;
    private final Supplier<? extends D> dataManagerFactory;
    private final BiConsumer<? super D, String> dataManagerBinder;
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
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataManagerFactory = Objects.requireNonNull(dataManagerFactory, "dataManagerFactory");
        this.dataManagerBinder = Objects.requireNonNull(dataManagerBinder, "dataManagerBinder");
        this.dataManagerQuiesce = Objects.requireNonNull(dataManagerQuiesce, "dataManagerQuiesce");
        this.dataManagerCleanup = Objects.requireNonNull(dataManagerCleanup, "dataManagerCleanup");
    }

    /** Creates the standard Velocity resource factory with framework-owned DataProvider discovery. */
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

    /** Creates the standard Velocity resource factory without a feature data resource. */
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
        String owner = requireText(featureName);
        FeatureServiceManager<FeatureId> services = new FeatureServiceManager<>();
        services.bindRegistries(capabilities, internalServices, FeatureId.of(owner));
        D dataManager = dataManagerFactory.get();
        if (dataManager != null) dataManagerBinder.accept(dataManager, owner);
        return new VelocityFeatureResources<>(
                new FeatureTaskManager(proxy.getScheduler(), plugin),
                new FeatureCommandManager(
                        plugin, proxy.getCommandManager(), commandOwnership, platformLogger, owner),
                new FeatureListenerManager(plugin, proxy.getEventManager()),
                dataManager,
                dataManager == null ? () -> { } : () -> dataManagerQuiesce.accept(dataManager),
                dataManager == null ? () -> { } : () -> dataManagerCleanup.accept(dataManager),
                new FeatureCacheManager(dataDirectory, logger),
                services
        );
    }

    public CommandOwnershipRegistry commandOwnership() {
        return commandOwnership;
    }

    private static String requireText(String value) {
        String clean = Objects.requireNonNull(value, "featureName").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("featureName must not be blank");
        return clean;
    }
}
