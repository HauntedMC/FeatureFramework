package nl.hauntedmc.featureframework.velocity.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.spi.lifecycle.FeatureResourceScopeCore;

import java.util.List;
import java.util.Objects;

/** Complete framework-owned resource scope for one Velocity feature instance. */
public class VelocityFeatureResources<D> implements FeatureLifecycleResources {
    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;
    private final FeatureListenerManager listenerManager;
    private final D dataManager;
    private final FeatureCacheManager cacheManager;
    private final FeatureServiceManager<FeatureId> apiManager;
    private final FeatureResourceScopeCore lifecycle;

    public VelocityFeatureResources(
            FeatureTaskManager taskManager,
            FeatureCommandManager commandManager,
            FeatureListenerManager listenerManager,
            D dataManager,
            Runnable quiesceData,
            Runnable cleanupData,
            FeatureCacheManager cacheManager,
            FeatureServiceManager<FeatureId> apiManager
    ) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.listenerManager = Objects.requireNonNull(listenerManager, "listenerManager");
        this.dataManager = dataManager;
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.apiManager = Objects.requireNonNull(apiManager, "apiManager");
        lifecycle = FeatureResourceScopeCore.create(
                listenerManager::quiesce,
                listenerManager::unregisterAllListeners,
                taskManager::quiesce,
                taskManager::cancelAllTasks,
                commandManager::quiesce,
                commandManager::unregisterAllBrigadierCommands,
                apiManager::quiesce,
                apiManager::unregisterAllServices,
                dataManager == null ? null : Objects.requireNonNull(quiesceData, "quiesceData"),
                dataManager == null ? null : Objects.requireNonNull(cleanupData, "cleanupData"),
                cacheManager::quiesce,
                cacheManager::cleanupAll,
                List.of()
        );
    }

    public FeatureTaskManager getTaskManager() { return taskManager; }
    public FeatureCommandManager getCommandManager() { return commandManager; }
    public FeatureListenerManager getListenerManager() { return listenerManager; }
    public D getDataManager() {
        if (dataManager == null) throw new IllegalStateException("Data manager is unavailable for this feature.");
        return dataManager;
    }
    public FeatureCacheManager getCacheManager() { return cacheManager; }
    public FeatureServiceManager<FeatureId> getApiManager() { return apiManager; }
    public FeatureResourceState state() { return lifecycle.state(); }
    public void quiesce() { lifecycle.quiesce(); }
    public void cleanup() { lifecycle.cleanup(); }
}
