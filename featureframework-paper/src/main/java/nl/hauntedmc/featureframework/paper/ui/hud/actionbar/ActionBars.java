package nl.hauntedmc.featureframework.paper.ui.hud.actionbar;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global access to the runtime-owned action-bar service.
 * Platform code must publish during startup and unpublish the same instance during disable.
 */
public final class ActionBars {
    private static final AtomicReference<ActionBarService> REF = new AtomicReference<>();

    private ActionBars() {
    }

    public static void bootstrap(@NotNull ActionBarService service) {
        Objects.requireNonNull(service, "service");
        if (!REF.compareAndSet(null, service)) {
            throw new IllegalStateException("Action-bar service is already published");
        }
    }

    public static void unpublish(@NotNull ActionBarService service) {
        if (!REF.compareAndSet(Objects.requireNonNull(service, "service"), null)) {
            throw new IllegalStateException("Cannot unpublish a different action-bar service");
        }
    }

    /** Returns the published runtime service or fails when called outside its lifecycle. */
    public static @NotNull ActionBarService service() {
        ActionBarService service = REF.get();
        if (service == null) {
            throw new IllegalStateException("Action-bar service is not available");
        }
        return service;
    }
}
