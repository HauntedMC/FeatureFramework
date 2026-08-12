package nl.hauntedmc.featureframework.spi.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureLifecycle;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.lifecycle.StandardFeatureResourceLifecycle;

import java.util.List;

/**
 * Shared resource-scope lifecycle policy used by Paper and Velocity feature resource facades.
 *
 * <p>Native managers remain platform-owned. This core only fixes lifecycle ordering and failure
 * aggregation: quiesce registrations first, then perform any established platform cleanup that must
 * precede listener teardown, followed by listener/task/command/service/data/cache cleanup.</p>
 */
public final class FeatureResourceScopeCore {
    private final FeatureLifecycle lifecycle;

    private FeatureResourceScopeCore(FeatureLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public static FeatureResourceScopeCore create(
            Runnable quiesceListeners,
            Runnable cleanupListeners,
            Runnable quiesceTasks,
            Runnable cleanupTasks,
            Runnable quiesceCommands,
            Runnable cleanupCommands,
            Runnable quiesceServices,
            Runnable cleanupServices,
            Runnable quiesceData,
            Runnable cleanupData,
            Runnable quiesceCaches,
            Runnable cleanupCaches,
            List<Runnable> cleanupBeforeListeners
    ) {
        return new FeatureResourceScopeCore(StandardFeatureResourceLifecycle.create(
                quiesceListeners,
                cleanupListeners,
                quiesceTasks,
                cleanupTasks,
                quiesceCommands,
                cleanupCommands,
                quiesceServices,
                cleanupServices,
                quiesceData,
                cleanupData,
                quiesceCaches,
                cleanupCaches,
                cleanupBeforeListeners));
    }

    public synchronized FeatureResourceState state() {
        return lifecycle.state();
    }

    public synchronized void quiesce() {
        lifecycle.quiesce();
    }

    public synchronized void cleanup() {
        lifecycle.cleanup();
    }
}
