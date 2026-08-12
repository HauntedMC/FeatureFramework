package nl.hauntedmc.featureframework.paper.ui.hud.toast;

import nl.hauntedmc.featureframework.paper.lifecycle.FeatureTaskManager;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToastNotifierTest {

    private final Plugin plugin = mock(Plugin.class);
    private final FeatureTaskManager tasks = mock(FeatureTaskManager.class);
    private final ToastNotifier notifier = new ToastNotifier(plugin, tasks);

    @Test
    void validatesInputBeforeSchedulingWork() {
        Player player = mock(Player.class);

        assertThrows(NullPointerException.class, () -> notifier.showToast(
                null,
                "{\"text\":\"Notice\"}",
                Material.PAPER,
                ToastNotifier.Frame.TASK,
                BukkitTime.ticks(1L),
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> notifier.showToast(
                player,
                "   ",
                Material.PAPER,
                ToastNotifier.Frame.TASK,
                BukkitTime.ticks(1L),
                null
        ));
        assertThrows(NullPointerException.class, () -> notifier.showToast(
                player,
                "{\"text\":\"Notice\"}",
                Material.PAPER,
                null,
                BukkitTime.ticks(1L),
                null
        ));
        assertThrows(NullPointerException.class, () -> notifier.showToast(
                player,
                "{\"text\":\"Notice\"}",
                Material.PAPER,
                ToastNotifier.Frame.TASK,
                null,
                null
        ));

        verifyNoInteractions(tasks);
    }

    @Test
    void delegatesRenderingToTheFeatureLifecycleScheduler() {
        Player player = mock(Player.class);
        when(plugin.namespace()).thenReturn("exampleplugin");
        when(player.getUniqueId()).thenReturn(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));

        notifier.showToast(
                player,
                "{\"text\":\"Notice\"}",
                null,
                ToastNotifier.Frame.GOAL,
                BukkitTime.ticks(0L),
                null
        );

        verify(tasks).scheduleOneTimeTask(any(Runnable.class));
    }
}
