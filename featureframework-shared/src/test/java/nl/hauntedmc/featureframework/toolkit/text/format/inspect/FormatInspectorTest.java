package nl.hauntedmc.featureframework.toolkit.text.format.inspect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.featureframework.toolkit.text.format.TextFormatter;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatInspectorTest {

    @Test
    void containsFormattingDetectsRequestedKinds() {
        assertTrue(FormatInspector.containsFormatting("&aHi", Set.of(TextFormatter.InputFormat.LEGACY_AMPERSAND)));
        assertTrue(FormatInspector.containsFormatting("§bHi", Set.of(TextFormatter.InputFormat.LEGACY_SECTION)));
        assertTrue(FormatInspector.containsFormatting("&x&1&2&3&4&5&6Hi", Set.of(TextFormatter.InputFormat.HEX_BUNGEE_AMP)));
        assertTrue(FormatInspector.containsFormatting("§x§1§2§3§4§5§6Hi", Set.of(TextFormatter.InputFormat.HEX_BUNGEE_SECTION)));
        assertTrue(FormatInspector.containsFormatting("&#A1B2C3", Set.of(TextFormatter.InputFormat.HEX_POUND)));
        assertTrue(FormatInspector.containsFormatting("<#A1B2C3>Hi", Set.of(TextFormatter.InputFormat.HEX_MINI)));
        assertTrue(FormatInspector.containsFormatting("<bold>Hi</bold>", Set.of(TextFormatter.InputFormat.MINIMESSAGE)));
        assertFalse(FormatInspector.containsFormatting("plain", Set.of(TextFormatter.InputFormat.MINIMESSAGE)));
        assertFalse(FormatInspector.containsFormatting("", Set.of(TextFormatter.InputFormat.MINIMESSAGE)));
        assertFalse(FormatInspector.containsFormatting("x", Set.of()));
        assertTrue(FormatInspector.containsAnyFormatting("&aHi"));
        assertFalse(FormatInspector.containsAnyFormatting("plain"));
    }

    @Test
    void hasFormattingDetectsStyleAndComponentAspectsRecursively() {
        Component styled = Component.text("x", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl("https://example.com"))
                .insertion("ins");
        Component withFont = Component.text("fonted").font(Key.key("minecraft", "default"));
        assertTrue(FormatInspector.hasFormatting(styled, Set.of(FormatInspector.ComponentAspect.COLOR)));
        assertTrue(FormatInspector.hasFormatting(styled, Set.of(FormatInspector.ComponentAspect.DECORATION)));
        assertTrue(FormatInspector.hasFormatting(styled, Set.of(FormatInspector.ComponentAspect.EVENTS)));
        assertTrue(FormatInspector.hasFormatting(styled, Set.of(FormatInspector.ComponentAspect.INSERTION)));
        assertTrue(FormatInspector.hasFormatting(withFont, Set.of(FormatInspector.ComponentAspect.FONT)));
        assertTrue(FormatInspector.hasAnyFormatting(styled));
        assertTrue(FormatInspector.hasFormatting(styled, Set.of()));

        Component types = Component.text("root")
                .append(Component.keybind("key.jump"))
                .append(Component.translatable("chat.type.text"))
                .append(Component.score("name", "objective"))
                .append(Component.selector("@a"))
                .append(Component.storageNBT("path", Key.key("minecraft", "storage")));

        assertTrue(FormatInspector.hasFormatting(types, Set.of(FormatInspector.ComponentAspect.KEYBIND)));
        assertTrue(FormatInspector.hasFormatting(types, Set.of(FormatInspector.ComponentAspect.TRANSLATABLE)));
        assertTrue(FormatInspector.hasFormatting(types, Set.of(FormatInspector.ComponentAspect.SCORE)));
        assertTrue(FormatInspector.hasFormatting(types, Set.of(FormatInspector.ComponentAspect.SELECTOR)));
        assertTrue(FormatInspector.hasFormatting(types, Set.of(FormatInspector.ComponentAspect.NBT)));
        assertTrue(FormatInspector.hasFormatting(types, EnumSet.allOf(FormatInspector.ComponentAspect.class)));
        assertFalse(FormatInspector.hasFormatting(Component.text("plain"), Set.of(FormatInspector.ComponentAspect.EVENTS)));
    }
}
