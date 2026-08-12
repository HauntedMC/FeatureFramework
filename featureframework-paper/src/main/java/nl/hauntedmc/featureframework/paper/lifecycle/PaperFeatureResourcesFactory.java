package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.integration.dataprovider.FeatureDataManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.paper.command.CommandLabelOwnership;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.paper.integration.dataprovider.PaperDataProviderApiResolver;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.FeatureGUIManager;
import nl.hauntedmc.featureframework.service.DefaultCapabilityRegistry;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.service.InternalServiceRegistry;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Assembles the standard framework-owned resource scope for a Paper feature.
 *
 * <p>Callers choose framework-owned DataProvider discovery or a no-data scope and provide only the
 * command-conflict policy. Task, command, listener, cache, GUI, and service ownership stay
 * centralized in FeatureFramework.</p>
 */
public final class PaperFeatureResourcesFactory<D> {
    private final Plugin plugin;
    private final Path dataDirectory;
    private final BrigadierDispatcher dispatcher;
    private final BooleanSupplier overwriteConflicts;
    private final FrameworkLogger logger;
    private final Supplier<? extends D> dataManagerFactory;
    private final BiConsumer<? super D, String> dataManagerBinder;
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
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.overwriteConflicts = Objects.requireNonNull(overwriteConflicts, "overwriteConflicts");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataManagerFactory = Objects.requireNonNull(dataManagerFactory, "dataManagerFactory");
        this.dataManagerBinder = Objects.requireNonNull(dataManagerBinder, "dataManagerBinder");
        this.dataManagerQuiesce = Objects.requireNonNull(dataManagerQuiesce, "dataManagerQuiesce");
        this.dataManagerCleanup = Objects.requireNonNull(dataManagerCleanup, "dataManagerCleanup");
    }

    /** Creates the standard Paper resource factory with framework-owned DataProvider discovery. */
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

    /** Creates the standard Paper resource factory without a feature data resource. */
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
        String owner = requireText(featureName);
        FeatureTaskManager tasks = new FeatureTaskManager(plugin);
        FeatureServiceManager<FeatureId> services = new FeatureServiceManager<>();
        services.bindRegistries(capabilities, internalServices, FeatureId.of(owner));
        D dataManager = dataManagerFactory.get();
        if (dataManager != null) dataManagerBinder.accept(dataManager, owner);
        return new PaperFeatureResources<>(
                tasks,
                new FeatureCommandManager(
                        plugin, dispatcher, commandOwnership, overwriteConflicts, logger),
                new FeatureListenerManager(plugin),
                dataManager,
                dataManager == null ? () -> { } : () -> dataManagerQuiesce.accept(dataManager),
                dataManager == null ? () -> { } : () -> dataManagerCleanup.accept(dataManager),
                new FeatureCacheManager(dataDirectory, logger),
                new FeatureGUIManager(plugin, tasks),
                services
        );
    }

    private static String requireText(String value) {
        String clean = Objects.requireNonNull(value, "featureName").trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("featureName must not be blank");
        return clean;
    }
}
