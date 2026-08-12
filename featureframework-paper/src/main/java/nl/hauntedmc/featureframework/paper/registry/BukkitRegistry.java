package nl.hauntedmc.featureframework.paper.registry;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/** Registry access used by Paper feature configuration and command adapters. */
public final class BukkitRegistry {

    private BukkitRegistry() {
    }

    /** Parses one canonical lower-case namespaced identifier. */
    public static NamespacedKey parseNamespacedKey(String input) {
        if (input == null || input.isBlank() || !input.equals(input.trim())
                || input.indexOf(':') <= 0 || !input.equals(input.toLowerCase(java.util.Locale.ROOT))) {
            return null;
        }
        return NamespacedKey.fromString(input);
    }

    public static Registry<@NotNull Sound> soundRegistry() {
        return registry(RegistryKey.SOUND_EVENT);
    }

    public static Registry<@NotNull Particle> particleRegistry() {
        return registry(RegistryKey.PARTICLE_TYPE);
    }

    public static Registry<@NotNull PotionEffectType> mobEffectRegistry() {
        return registry(RegistryKey.MOB_EFFECT);
    }

    private static <T extends org.bukkit.Keyed> Registry<@NotNull T> registry(RegistryKey<@NotNull T> key) {
        return RegistryAccess.registryAccess().getRegistry(key);
    }
}
