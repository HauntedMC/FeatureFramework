package nl.hauntedmc.featureframework.paper.ui.hud.toast;

import nl.hauntedmc.featureframework.paper.effect.sound.SoundProfile;
import nl.hauntedmc.featureframework.paper.lifecycle.FeatureTaskManager;
import nl.hauntedmc.featureframework.paper.time.BukkitTime;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature-owned, lifecycle-safe Paper toast renderer. */
public final class ToastNotifier implements AutoCloseable {
    private final Plugin plugin;
    private final FeatureTaskManager tasks;
    private final Map<NamespacedKey, UUID> activeToasts = new ConcurrentHashMap<>();

    public ToastNotifier(Plugin plugin, FeatureTaskManager tasks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    public enum Frame {
        TASK,
        GOAL,
        CHALLENGE
    }

    /** Displays an ephemeral toast and removes its temporary advancement after {@code duration}. */
    public void showToast(
            Player player,
            String titleJson,
            Material icon,
            Frame frame,
            BukkitTime duration,
            SoundProfile soundProfile
    ) {
        Player target = Objects.requireNonNull(player, "player");
        String title = requireText(titleJson, "titleJson");
        Material displayIcon = Objects.requireNonNullElse(icon, Material.PAPER);
        Frame displayFrame = Objects.requireNonNull(frame, "frame");
        BukkitTime requestedDuration = Objects.requireNonNull(duration, "duration");
        BukkitTime displayDuration = BukkitTime.ticks(Math.max(1L, requestedDuration.toTicks()));
        NamespacedKey key = new NamespacedKey(
                plugin,
                "runtime/" + target.getUniqueId() + "_" + UUID.randomUUID().toString().replace("-", "")
        );

        tasks.scheduleOneTimeTask(() -> showNow(
                target,
                key,
                title,
                displayIcon,
                displayFrame,
                displayDuration,
                soundProfile
        ));
    }

    private void showNow(
            Player player,
            NamespacedKey key,
            String titleJson,
            Material icon,
            Frame frame,
            BukkitTime duration,
            SoundProfile soundProfile
    ) {
        if (!player.isOnline()) {
            return;
        }

        String json = """
                {
                  "display": {
                    "icon": { "id": "%s" },
                    "title": %s,
                    "description": { "text": "" },
                    "frame": "%s",
                    "announce_to_chat": false,
                    "show_toast": true,
                    "hidden": true
                  },
                  "criteria": { "impossible": { "trigger": "minecraft:impossible" } }
                }
                """.formatted(icon.getKey(), titleJson, frame.name().toLowerCase(java.util.Locale.ROOT));

        @SuppressWarnings("deprecation")
        var unsafe = Bukkit.getUnsafe();
        Advancement advancement;
        try {
            unsafe.loadAdvancement(key, json);
            advancement = Bukkit.getAdvancement(key);
        } catch (RuntimeException failure) {
            removeAdvancement(key);
            throw failure;
        }
        if (advancement == null) {
            removeAdvancement(key);
            return;
        }

        activeToasts.put(key, player.getUniqueId());
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        revokeAwardedCriteria(progress);
        progress.awardCriteria("impossible");
        if (soundProfile != null) {
            soundProfile.play(player);
        }

        try {
            tasks.scheduleDelayedTask(() -> revokeAndRemove(player, key), duration);
        } catch (RuntimeException failure) {
            revokeAndRemove(player, key);
            throw failure;
        }
    }

    private void revokeAndRemove(Player player, NamespacedKey key) {
        try {
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement != null && player.isOnline()) {
                revokeAwardedCriteria(player.getAdvancementProgress(advancement));
            }
        } finally {
            removeAdvancement(key);
            activeToasts.remove(key);
        }
    }

    private static void revokeAwardedCriteria(AdvancementProgress progress) {
        for (String criterion : progress.getAwardedCriteria()) {
            progress.revokeCriteria(criterion);
        }
    }

    @SuppressWarnings("deprecation")
    private static void removeAdvancement(NamespacedKey key) {
        try {
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (RuntimeException ignored) {
            // Advancement cleanup is best effort during shutdown and failed construction.
        }
    }

    @Override
    public void close() {
        for (Map.Entry<NamespacedKey, UUID> toast : Map.copyOf(activeToasts).entrySet()) {
            Player player = Bukkit.getPlayer(toast.getValue());
            if (player == null) {
                removeAdvancement(toast.getKey());
                activeToasts.remove(toast.getKey());
            } else {
                revokeAndRemove(player, toast.getKey());
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
