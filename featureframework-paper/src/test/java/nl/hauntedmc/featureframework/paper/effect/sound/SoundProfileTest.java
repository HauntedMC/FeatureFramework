package nl.hauntedmc.featureframework.paper.effect.sound;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoundProfileTest {

    @Test
    void normalizesPitchAtConstructionAndUsesExplicitDefaultCategory() {
        SoundProfile profile = SoundProfile.of(" minecraft:block.note_block.pling ", 0.8F, 2.2F);

        assertEquals("minecraft:block.note_block.pling", profile.sound());
        assertEquals(SoundCategory.MASTER, profile.category());
        assertEquals(2.0F, profile.pitch());
    }

    @Test
    void rejectsInvalidNumericParameters() {
        String sound = "minecraft:ui.button.click";
        assertThrows(IllegalArgumentException.class,
                () -> SoundProfile.of(sound, -0.1F, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> SoundProfile.of(sound, Float.NaN, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> SoundProfile.of(sound, 1.0F, Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsBlankSoundNames() {
        assertThrows(IllegalArgumentException.class, () -> SoundProfile.of(" ", 1.0F, 1.0F));
    }

    @Test
    void withMethodsCreateValidatedValueCopies() {
        SoundProfile original = SoundProfile.of(
                "minecraft:block.note_block.pling",
                SoundCategory.PLAYERS,
                0.8F,
                1.4F
        );

        SoundProfile changed = original.withVolume(0.5F).withPitch(0.25F);

        assertNotSame(original, changed);
        assertEquals(0.8F, original.volume());
        assertEquals(0.5F, changed.volume());
        assertEquals(0.5F, changed.pitch());
    }

    @Test
    void playsForPlayersAndWorldsWithTheStoredParameters() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        String sound = "minecraft:block.amethyst_block.chime";
        when(player.getLocation()).thenReturn(location);
        SoundProfile profile = SoundProfile.of(
                sound,
                SoundCategory.PLAYERS,
                0.7F,
                1.2F
        );

        profile.play(player);
        profile.play(world, location);

        verify(player).playSound(location, sound,
                SoundCategory.PLAYERS, 0.7F, 1.2F);
        verify(world).playSound(location, sound,
                SoundCategory.PLAYERS, 0.7F, 1.2F);
    }

    @Test
    void rejectsMissingPlaybackTargets() {
        SoundProfile profile = SoundProfile.of("minecraft:ui.button.click", 1.0F, 1.0F);

        assertThrows(NullPointerException.class, () -> profile.play((Player) null));
        assertThrows(NullPointerException.class, () -> profile.play((World) null, mock(Location.class)));
    }
}
