package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Tracks and safely unregisters every Bukkit listener owned by one feature. */
public class FeatureListenerManager {
    private final Plugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureListenerManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void registerListener(Listener listener) {
        requireOpen();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        track(listener);
    }

    public synchronized <T extends Event> void registerEvent(
            Listener listener,
            Class<T> eventType,
            EventPriority priority,
            boolean ignoreCancelled,
            Consumer<T> handler
    ) {
        requireOpen();
        plugin.getServer().getPluginManager().registerEvent(
                eventType, listener, priority,
                (ignored, event) -> {
                    if (eventType.isInstance(event)) handler.accept(eventType.cast(event));
                },
                plugin, ignoreCancelled
        );
        track(listener);
    }

    public synchronized void unregisterListener(Listener listener) {
        if (listener == null) return;
        HandlerList.unregisterAll(listener);
        listeners.remove(listener);
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public synchronized void unregisterAllListeners() {
        quiesce();
        Throwable failure = null;
        for (Listener listener : List.copyOf(listeners)) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Throwable stepFailure) {
                if (failure == null) failure = stepFailure;
                else failure.addSuppressed(stepFailure);
            }
        }
        listeners.clear();
        state = FeatureResourceState.CLOSED;
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized int getRegisteredListenerCount() { return listeners.size(); }
    public synchronized FeatureResourceState state() { return state; }

    private void track(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) throw new IllegalStateException("Listener manager is " + state);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
