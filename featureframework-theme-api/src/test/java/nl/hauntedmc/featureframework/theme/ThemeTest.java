package nl.hauntedmc.featureframework.theme;

import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTest {
    @Test
    void buildsImmutableCaseInsensitiveThemeRegistry() {
        Theme haunted = Theme.builder("HauntedMC")
                .solid("Brand", "#A855F7")
                .gradient("Header", List.of(TextColor.color(0xA855F7), TextColor.color(0x38BDF8)))
                .build();
        ThemeRegistry registry = ThemeRegistry.of(List.of(haunted));
        ThemeRegistry builtRegistry = ThemeRegistry.builder().theme(haunted).build();
        ThemeRegistry includedRegistry = ThemeRegistry.builder().include(registry).build();

        assertEquals(0xA855F7, ((ThemeColor.Solid) registry.item("hauntedmc", "BRAND")
                .orElseThrow().color()).color().value());
        assertEquals(List.of("Brand", "Header"), haunted.items().stream()
                .map(item -> item.id().value()).toList());
        assertSame(haunted.items(), haunted.items());
        assertSame(registry.themes(), registry.themes());
        assertTrue(registry.theme("HAUNTEDMC").isPresent());
        assertTrue(builtRegistry.theme("hauntedmc").isPresent());
        assertTrue(includedRegistry.theme("hauntedmc").isPresent());
    }

    @Test
    void rejectsInvalidAndDuplicateIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> Theme.builder("bad:id"));
        assertThrows(IllegalArgumentException.class, () -> Theme.builder("Empty").build());
        assertThrows(IllegalArgumentException.class, () -> Theme.builder("Valid")
                .solid("Brand", TextColor.color(0))
                .solid("brand", TextColor.color(1)));
        Theme first = Theme.builder("Example").solid("Color", TextColor.color(0)).build();
        Theme duplicate = Theme.builder("example").solid("Other", TextColor.color(1)).build();
        assertThrows(IllegalArgumentException.class, () -> ThemeRegistry.of(List.of(first, duplicate)));
    }

    @Test
    void validatesEffectArgumentsAndCopiesColorLists() {
        assertThrows(IllegalArgumentException.class,
                () -> ThemeColor.gradient(List.of(TextColor.color(0))));
        assertThrows(IllegalArgumentException.class,
                () -> ThemeColor.transition(List.of(TextColor.color(0), TextColor.color(1)), 1.1D));
        assertThrows(IllegalArgumentException.class, () -> ThemeColor.solid("A855F7"));
        assertThrows(IllegalArgumentException.class, () -> ThemeColor.solid(0x1000000));
    }
}
