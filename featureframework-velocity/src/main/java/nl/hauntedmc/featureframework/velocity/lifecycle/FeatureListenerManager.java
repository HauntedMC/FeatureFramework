package nl.hauntedmc.featureframework.velocity.lifecycle;

import com.velocitypowered.api.event.EventManager;
import nl.hauntedmc.featureframework.lifecycle.FeatureRegistrationTracker;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;

import java.util.Objects;

/** Tracks Velocity listeners while delegating ownership state to the shared registration tracker. */
public class FeatureListenerManager {
    private final Object plugin;
    private final EventManager eventManager;
    private final FeatureRegistrationTracker<Object> listeners = new FeatureRegistrationTracker<>(false);

    public FeatureListenerManager(Object plugin, EventManager eventManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager");
    }

    public void registerListener(Object listener) {
        listeners.register(listener, required -> eventManager.register(plugin, required));
    }

    public void quiesce() { listeners.quiesce(); }

    public void unregisterAllListeners() {
        listeners.unregisterAll(listener -> eventManager.unregisterListener(plugin, listener));
    }

    public int getRegisteredListenerCount() { return listeners.size(); }
    public FeatureResourceState state() { return listeners.state(); }
}
