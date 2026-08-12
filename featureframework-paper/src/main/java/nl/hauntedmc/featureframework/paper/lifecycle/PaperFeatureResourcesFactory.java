package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceFactoryCore;
import nl.hauntedmc.featureframework.paper.command.CommandLabelOwnership;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.integration.dataprovider.PaperDataProviderApiResolver;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.FeatureGUIManager;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Assembles Paper-native resources around the shared feature resource core. */
public final class PaperFeatureResourcesFactory<D> {
    private final Plugin plugin;
    private final BrigadierDispatcher dispatcher;
    private final BooleanSupplier overwriteConflicts;
    private final FrameworkLogger logger;
    private final FeatureResourceFactoryCore<D> core;
    private final Consumer<? super D> dataManagerQuiesce;
    private final Consumer<? super D> dataManagerCleanup;
    private final CommandLabelOwnership commandOwnership = new CommandLabelOwnership();

    private PaperFeatureResourcesFactory(
            Plugin plugin,
            Path dataDirectory,
            BrigadierDispatcher dispatcher,
            BooleanSupplier overwriteConflicts,
            FrameworkLogger logger,
            Supplier<? extends D> dataManagerFactory,
            BiConsumer<? super D, String> dataManagerBinder,
            Consumer<? super D> dataManagerQuiesce,
            Consumer<? super D> dataManagerCleanup
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.overwriteConflicts = Objects.requireNonNull(overwriteConflicts, "overwriteConflicts");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.core = new FeatureResourceFactoryCore<>(
                dataDirectory, logger, dataManagerFactory, dataManagerBinder);
        this.dataManagerQuiesce = Objects.requireNonNull(dataManagerQuiesce, "dataManagerQuiesce");
        this.dataManagerCleanup = Objects.requireNonNull(dataManagerCleanup, "dataManagerCleanup");
    }

    public static PaperFeatureResourcesFactory<FeatureDataManager> withDataProvider(
            Plugin plugin,
            Path dataDirectory,
            BrigadierDispatcher dispatcher,
            BooleanSupplier overwriteConflicts,
            FrameworkLogger logger,
            BooleanSupplier dataProviderAvailable,
            Supplier<String> schemaMode
    ) {
        Objects.requireNonNull(dataProviderAvailable, "dataProviderAvailable");
        Objects.requireNonNull(schemaMode, "schemaMode");
        return new PaperFeatureResourcesFactory<>(
                plugin,
                dataDirectory,
                dispatcher,
                overwriteConflicts,
                logger,
                () -> dataProviderAvailable.getAsBoolean()
                        ? new FeatureDataManager(
                                plugin,
                                PaperDataProviderApiResolver.supplier(
                                        () -> plugin.getServer().getServicesManager(), logger::warn),
                                logger,
                                schemaMode)
                        : null,
                FeatureDataManager::bindToFeature,
                FeatureDataManager::quiesce,
                FeatureDataManager::closeAllDataResources
        );
    }

    public static PaperFeatureResourcesFactory<Void> withoutDataProvider(
            Plugin plugin,
            Path dataDirectory,
            BrigadierDispatcher dispatcher,
            BooleanSupplier overwriteConflicts,
            FrameworkLogger logger
    ) {
        return new PaperFeatureResourcesFactory<>(
                plugin,
                dataDirectory,
                dispatcher,
                overwriteConflicts,
                logger,
                () -> null,
                (ignored, featureName) -> { },
                ignored -> { },
                ignored -> { }
        );
    }

    public PaperFeatureResources<D> create(
            String featureName,
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internalServices
    ) {
        FeatureResourceFactoryCore.Bundle<D> common = core.create(featureName, capabilities, internalServices);
        D dataManager = common.dataManager();
        FeatureTaskManager tasks = new FeatureTaskManager(plugin);
        return new PaperFeatureResources<>(
                tasks,
                new FeatureCommandManager(
                        plugin, dispatcher, commandOwnership, overwriteConflicts, logger),
                new FeatureListenerManager(plugin),
                dataManager,
                dataManager == null ? () -> { } : () -> dataManagerQuiesce.accept(dataManager),
                dataManager == null ? () -> { } : () -> dataManagerCleanup.accept(dataManager),
                common.cacheManager(),
                new FeatureGUIManager(plugin, tasks),
                common.serviceManager()
        );
    }
}
