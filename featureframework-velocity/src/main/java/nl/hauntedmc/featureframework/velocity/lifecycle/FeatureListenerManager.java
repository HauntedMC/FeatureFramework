package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.event.EventManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tracks and safely unregisters every Velocity listener owned by one feature. */
public class FeatureListenerManager {
    private final Object plugin;
    private final EventManager eventManager;
    private final List<Object> listeners = new ArrayList<>();
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureListenerManager(Object plugin, EventManager eventManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager");
    }

    public synchronized void registerListener(Object listener) {
        requireOpen();
        Object required = Objects.requireNonNull(listener, "listener");
        eventManager.register(plugin, required);
        listeners.add(required);
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public synchronized void unregisterAllListeners() {
        quiesce();
        Throwable failure = null;
        for (Object listener : List.copyOf(listeners)) {
            try {
                eventManager.unregisterListener(plugin, listener);
                listeners.remove(listener);
            } catch (Throwable stepFailure) {
                if (failure == null) failure = stepFailure;
                else failure.addSuppressed(stepFailure);
            }
        }
        if (failure == null && listeners.isEmpty()) state = FeatureResourceState.CLOSED;
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized int getRegisteredListenerCount() { return listeners.size(); }
    public synchronized FeatureResourceState state() { return state; }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) throw new IllegalStateException("Listener manager is " + state);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
