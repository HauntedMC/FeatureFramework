package nl.hauntedmc.featureframework.paper.effect.sound;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Immutable, validated parameters for one Paper sound playback operation.
 *
 * <p>Pitch is normalized to Paper's supported {@code [0.5, 2.0]} range when the profile is
 * created. Volume must be finite and non-negative. A category is always explicit so playback
 * never depends on overload-specific defaults.</p>
 */
public record SoundProfile(String sound, SoundCategory category, float volume, float pitch) {
    private static final float MINIMUM_PITCH = 0.5F;
    private static final float MAXIMUM_PITCH = 2.0F;

    public SoundProfile {
        sound = requireSound(sound);
        Objects.requireNonNull(category, "category");
        volume = requireVolume(volume);
        pitch = normalizePitch(pitch);
    }

    /** Creates a profile in the master sound category. */
    public static SoundProfile of(String sound, float volume, float pitch) {
        return new SoundProfile(sound, SoundCategory.MASTER, volume, pitch);
    }

    public static SoundProfile of(String sound, SoundCategory category, float volume, float pitch) {
        return new SoundProfile(sound, category, volume, pitch);
    }

    public SoundProfile withSound(String replacement) {
        return new SoundProfile(replacement, category, volume, pitch);
    }

    public SoundProfile withCategory(SoundCategory replacement) {
        return new SoundProfile(sound, replacement, volume, pitch);
    }

    public SoundProfile withVolume(float replacement) {
        return new SoundProfile(sound, category, replacement, pitch);
    }

    public SoundProfile withPitch(float replacement) {
        return new SoundProfile(sound, category, volume, replacement);
    }

    /** Plays at the player's current location. */
    public void play(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        play(target, target.getLocation());
    }

    /** Plays only for the target player at the supplied location. */
    public void play(Player player, Location location) {
        Objects.requireNonNull(player, "player")
                .playSound(Objects.requireNonNull(location, "location"), sound, category, volume, pitch);
    }

    /** Plays in the world at the supplied location for nearby players. */
    public void play(World world, Location location) {
        Objects.requireNonNull(world, "world")
                .playSound(Objects.requireNonNull(location, "location"), sound, category, volume, pitch);
    }

    private static float requireVolume(float value) {
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException("volume must be finite and non-negative");
        }
        return value;
    }

    private static String requireSound(String value) {
        Objects.requireNonNull(value, "sound");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sound must not be blank");
        }
        return normalized;
    }

    private static float normalizePitch(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("pitch must be finite");
        }
        return Math.clamp(value, MINIMUM_PITCH, MAXIMUM_PITCH);
    }
}
