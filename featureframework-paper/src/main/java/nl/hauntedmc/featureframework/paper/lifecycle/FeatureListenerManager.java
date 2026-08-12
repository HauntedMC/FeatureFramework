package nl.hauntedmc.featureframework.paper.lifecycle;

import nl.hauntedmc.featureframework.lifecycle.FeatureRegistrationTracker;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Consumer;

/** Tracks Bukkit listeners while delegating ownership state to the shared registration tracker. */
public class FeatureListenerManager {
    private final Plugin plugin;
    private final FeatureRegistrationTracker<Listener> listeners = new FeatureRegistrationTracker<>(true);

    public FeatureListenerManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void registerListener(Listener listener) {
        listeners.register(listener, required ->
                plugin.getServer().getPluginManager().registerEvents(required, plugin));
    }

    public <T extends Event> void registerEvent(
            Listener listener,
            Class<T> eventType,
            EventPriority priority,
            boolean ignoreCancelled,
            Consumer<T> handler
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(handler, "handler");
        listeners.register(listener, required -> plugin.getServer().getPluginManager().registerEvent(
                eventType,
                required,
                priority,
                (ignored, event) -> {
                    if (eventType.isInstance(event)) handler.accept(eventType.cast(event));
                },
                plugin,
                ignoreCancelled));
    }

    public void unregisterListener(Listener listener) {
        listeners.unregister(listener, HandlerList::unregisterAll);
    }

    public void quiesce() { listeners.quiesce(); }
    public void unregisterAllListeners() { listeners.unregisterAll(HandlerList::unregisterAll); }
    public int getRegisteredListenerCount() { return listeners.size(); }
    public FeatureResourceState state() { return listeners.state(); }
}
