package nl.hauntedmc.featureframework.paper.lifecycle;

import org.bukkit.Server;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureListenerManagerTest {

    @Test
    void runtimeSubtypeHandlerIgnoresSharedParentHandlerListEvents() throws Exception {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Listener listener = mock(Listener.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        AtomicReference<PlayerDeathEvent> received = new AtomicReference<>();
        FeatureListenerManager manager = new FeatureListenerManager(plugin);

        manager.registerEvent(listener, PlayerDeathEvent.class, EventPriority.NORMAL, false, received::set);

        ArgumentCaptor<EventExecutor> executor = ArgumentCaptor.forClass(EventExecutor.class);
        verify(pluginManager).registerEvent(
                eq(PlayerDeathEvent.class), eq(listener), eq(EventPriority.NORMAL), executor.capture(),
                eq(plugin), eq(false));

        executor.getValue().execute(listener, mock(EntityDeathEvent.class));
        assertNull(received.get());

        PlayerDeathEvent playerDeath = mock(PlayerDeathEvent.class);
        executor.getValue().execute(listener, playerDeath);
        assertSame(playerDeath, received.get());
    }
}
