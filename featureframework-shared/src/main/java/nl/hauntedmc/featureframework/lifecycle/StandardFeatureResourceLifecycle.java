package nl.hauntedmc.featureframework.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds the standard resource lifecycle shared by Paper and Velocity feature scopes. */
public final class StandardFeatureResourceLifecycle {
    private StandardFeatureResourceLifecycle() {
    }

    public static FeatureLifecycle create(
            Runnable listenerQuiesce,
            Runnable listenerCleanup,
            Runnable taskQuiesce,
            Runnable taskCleanup,
            Runnable commandQuiesce,
            Runnable commandCleanup,
            Runnable serviceQuiesce,
            Runnable serviceCleanup,
            Runnable dataQuiesce,
            Runnable dataCleanup,
            Runnable cacheQuiesce,
            Runnable cacheCleanup,
            List<? extends Runnable> cleanupBeforeListeners
    ) {
        List<Runnable> quiesce = new ArrayList<>(List.of(
                require(listenerQuiesce), require(taskQuiesce), require(commandQuiesce), require(serviceQuiesce)));
        List<Runnable> cleanup = new ArrayList<>();
        Objects.requireNonNull(cleanupBeforeListeners, "cleanupBeforeListeners").forEach(step -> cleanup.add(require(step)));
        cleanup.add(require(listenerCleanup));
        cleanup.add(require(taskCleanup));
        cleanup.add(require(commandCleanup));
        cleanup.add(require(serviceCleanup));
        if (dataQuiesce != null || dataCleanup != null) {
            quiesce.add(require(dataQuiesce));
            cleanup.add(require(dataCleanup));
        }
        quiesce.add(require(cacheQuiesce));
        cleanup.add(require(cacheCleanup));
        return new FeatureLifecycle(quiesce, cleanup);
    }

    private static Runnable require(Runnable step) {
        return Objects.requireNonNull(step, "lifecycle step");
    }
}
