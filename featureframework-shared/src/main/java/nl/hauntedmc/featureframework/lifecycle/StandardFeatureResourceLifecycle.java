package nl.hauntedmc.featureframework.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds the standard resource lifecycle shared by Paper and Velocity feature scopes. */
public final class StandardFeatureResourceLifecycle {
    private StandardFeatureResourceLifecycle() {
    }

    /**
     * Creates the standard lifecycle from positional callbacks.
     *
     * <p>This method is retained for source and binary compatibility. New framework code should use
     * {@link #builder()} so lifecycle roles are named at the call site.</p>
     */
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

    /** Returns a named builder for the standard resource lifecycle. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named construction API for platform resource scopes.
     *
     * <p>Listeners, tasks, commands, services, and caches are required. Data callbacks are optional
     * but must be configured as a pair. Pre-listener cleanup steps run after quiescing and before
     * listener teardown, preserving the established Paper GUI shutdown ordering.</p>
     */
    public static final class Builder {
        private Runnable listenerQuiesce;
        private Runnable listenerCleanup;
        private Runnable taskQuiesce;
        private Runnable taskCleanup;
        private Runnable commandQuiesce;
        private Runnable commandCleanup;
        private Runnable serviceQuiesce;
        private Runnable serviceCleanup;
        private Runnable dataQuiesce;
        private Runnable dataCleanup;
        private Runnable cacheQuiesce;
        private Runnable cacheCleanup;
        private final List<Runnable> cleanupBeforeListeners = new ArrayList<>();

        private Builder() {
        }

        public Builder listeners(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "listener quiesce");
            Objects.requireNonNull(cleanup, "listener cleanup");
            listenerQuiesce = quiesce;
            listenerCleanup = cleanup;
            return this;
        }

        public Builder tasks(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "task quiesce");
            Objects.requireNonNull(cleanup, "task cleanup");
            taskQuiesce = quiesce;
            taskCleanup = cleanup;
            return this;
        }

        public Builder commands(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "command quiesce");
            Objects.requireNonNull(cleanup, "command cleanup");
            commandQuiesce = quiesce;
            commandCleanup = cleanup;
            return this;
        }

        public Builder services(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "service quiesce");
            Objects.requireNonNull(cleanup, "service cleanup");
            serviceQuiesce = quiesce;
            serviceCleanup = cleanup;
            return this;
        }

        public Builder data(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "data quiesce");
            Objects.requireNonNull(cleanup, "data cleanup");
            dataQuiesce = quiesce;
            dataCleanup = cleanup;
            return this;
        }

        public Builder caches(Runnable quiesce, Runnable cleanup) {
            Objects.requireNonNull(quiesce, "cache quiesce");
            Objects.requireNonNull(cleanup, "cache cleanup");
            cacheQuiesce = quiesce;
            cacheCleanup = cleanup;
            return this;
        }

        public Builder beforeListenerCleanup(Runnable cleanup) {
            cleanupBeforeListeners.add(Objects.requireNonNull(cleanup, "pre-listener cleanup"));
            return this;
        }

        public FeatureLifecycle build() {
            requireConfigured(listenerQuiesce, listenerCleanup, "listeners");
            requireConfigured(taskQuiesce, taskCleanup, "tasks");
            requireConfigured(commandQuiesce, commandCleanup, "commands");
            requireConfigured(serviceQuiesce, serviceCleanup, "services");
            requireConfigured(cacheQuiesce, cacheCleanup, "caches");
            return create(
                    listenerQuiesce,
                    listenerCleanup,
                    taskQuiesce,
                    taskCleanup,
                    commandQuiesce,
                    commandCleanup,
                    serviceQuiesce,
                    serviceCleanup,
                    dataQuiesce,
                    dataCleanup,
                    cacheQuiesce,
                    cacheCleanup,
                    cleanupBeforeListeners
            );
        }

        private static void requireConfigured(Runnable quiesce, Runnable cleanup, String role) {
            if (quiesce == null || cleanup == null) {
                throw new IllegalStateException("Resource lifecycle role '" + role + "' was not configured");
            }
        }
    }

    private static Runnable require(Runnable step) {
        return Objects.requireNonNull(step, "lifecycle step");
    }
}
