package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycle;
import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycleResources;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.lifecycle.FeatureCacheManager;
import nl.hauntedmc.featureframework.lifecycle.StandardFeatureResourceLifecycle;
import nl.hauntedmc.featureframework.paper.command.FeatureCommandManager;
import nl.hauntedmc.featureframework.service.FeatureServiceManager;
import nl.hauntedmc.featureframework.resource.FeatureResourceExtensions;
import nl.hauntedmc.featureframework.resource.FeatureResourceOwner;

import java.util.List;
import java.util.Objects;

/** Complete framework-owned resource scope for one Paper feature instance. */
public final class PaperFeatureResources implements FeatureLifecycleResources {
    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;
    private final FeatureListenerManager listenerManager;
    private final FeatureCacheManager cacheManager;
    private final FeatureServiceManager<FeatureId> apiManager;
    private final FeatureResourceOwner ownership;
    private final FeatureResourceExtensions extensions;
    private final FeatureLifecycle lifecycle;

    public PaperFeatureResources(
            FeatureTaskManager taskManager,
            FeatureCommandManager commandManager,
            FeatureListenerManager listenerManager,
            FeatureCacheManager cacheManager,
            FeatureServiceManager<FeatureId> apiManager,
            FeatureResourceOwner ownership,
            FeatureResourceExtensions extensions
    ) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.listenerManager = Objects.requireNonNull(listenerManager, "listenerManager");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.apiManager = Objects.requireNonNull(apiManager, "apiManager");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        lifecycle = StandardFeatureResourceLifecycle.create(
                listenerManager::quiesce,
                listenerManager::unregisterAllListeners,
                taskManager::quiesce,
                taskManager::cancelAllTasks,
                commandManager::quiesce,
                commandManager::unregisterAllBrigadierCommands,
                apiManager::quiesce,
                apiManager::unregisterAllServices,
                ownership::quiesce,
                ownership::cleanup,
                cacheManager::quiesce,
                cacheManager::cleanupAll,
                List.of()
        );
    }

    public FeatureTaskManager tasks() { return taskManager; }
    public FeatureCommandManager commands() { return commandManager; }
    public FeatureListenerManager listeners() { return listenerManager; }
    public FeatureCacheManager caches() { return cacheManager; }
    public FeatureServiceManager<FeatureId> capabilities() { return apiManager; }
    public FeatureResourceOwner ownership() { return ownership; }
    public FeatureResourceExtensions extensions() { return extensions; }
    public synchronized FeatureResourceState state() { return lifecycle.state(); }
    public synchronized void quiesce() { lifecycle.quiesce(); }
    public synchronized void cleanup() { lifecycle.cleanup(); }
}
