package nl.hauntedmc.featureframework.localization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.featureframework.theme.Theme;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemeTagExpanderTest {
    private static final Theme HAUNTED = Theme.builder("HauntedMC")
            .solid("Brand", TextColor.color(0xA855F7))
            .solid("Text", TextColor.color(0xE2E8F0))
            .gradient("Heading", List.of(TextColor.color(0xA855F7), TextColor.color(0x38BDF8)))
            .transition("Current", List.of(TextColor.color(0xA855F7), TextColor.color(0x38BDF8)), 0.5D)
            .rainbow("Party", 2, true)
            .build();

    @Test
    void expandsRequestedPersistentColorExample() {
        String expanded = expander(new ArrayList<>()).expand(
                "<HauntedMC:Brand>◆ Friends  <HauntedMC:Text>Er zijn geen vrienden online");

        assertEquals("<color:#A855F7>◆ Friends  <color:#E2E8F0>Er zijn geen vrienden online", expanded);
        Component component = MiniMessage.miniMessage().deserialize(expanded);
        assertEquals("◆ Friends  Er zijn geen vrienden online",
                PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void expandsNestedScopedEffectsAndMixedCaseLookups() {
        String expanded = expander(new ArrayList<>()).expand(
                "<hauntedmc:heading>A<HAUNTEDMC:Brand>B</HauntedMC>C</HauntedMC>D");

        assertEquals("<gradient:#A855F7:#38BDF8>A<color:#A855F7>B</color>C</gradient>D", expanded);
        assertEquals("ABCD", PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(expanded)));
    }

    @Test
    void stripsOnlyInvalidMarkupAndDeduplicatesDiagnostics() {
        List<String> warnings = new ArrayList<>();
        ThemeTagExpander expander = expander(warnings);

        assertEquals("Unknown", expander.expand("<HauntedMC:Missing>Unknown</HauntedMC>"));
        assertEquals("Again", expander.expand("<hauntedmc:missing>Again</HauntedMC>"));
        assertEquals("tail", expander.expand("</HauntedMC>tail"));
        assertEquals("tail", expander.expand("<HauntedMC>tail</HauntedMC>"));
        assertEquals("tail", expander.expand("<HauntedMC:Brand:extra>tail</HauntedMC>"));
        assertEquals("tail", PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(expander.expand("<HauntedMC:Brand>tail</HauntedMC:Brand>"))));
        assertEquals(5, warnings.size());
    }

    @Test
    void leavesEscapedThemeTagsUntouched() {
        String expanded = expander(new ArrayList<>()).expand("\\<HauntedMC:Brand>literal");

        assertEquals("\\<HauntedMC:Brand>literal", expanded);
        assertFalse(expanded.contains("<color:"));
    }

    @Test
    void supportsMultipleThemes() {
        Theme other = Theme.builder("Other").solid("Primary", TextColor.color(0x010203)).build();
        ThemeTagExpander expander = new ThemeTagExpander(
                ThemeRegistry.of(List.of(HAUNTED, other)), ignored -> { });

        assertTrue(expander.expand("<Other:Primary>x").startsWith("<color:#010203>"));
    }

    @Test
    void preservesMiniMessageTagsAndRejectsConflictingThemeIdentifiers() {
        assertEquals("<click:run_command:'/help'>Help</click>",
                expander(new ArrayList<>()).expand("<click:run_command:'/help'>Help</click>"));
        Theme conflicting = Theme.builder("click").solid("Primary", TextColor.color(0x010203)).build();
        assertThrows(IllegalArgumentException.class,
                () -> new ThemeTagExpander(ThemeRegistry.of(List.of(conflicting)), ignored -> { }));
    }

    @Test
    void boundsDistinctDiagnostics() {
        List<String> warnings = new ArrayList<>();
        ThemeTagExpander expander = expander(warnings);

        for (int index = 0; index < 300; index++) {
            expander.expand("<HauntedMC:Missing" + index + ">value");
        }

        assertEquals(257, warnings.size());
        assertEquals("Further distinct theme-tag warnings are suppressed.", warnings.getLast());
    }

    @Test
    void expandsTransitionAsPersistentMiniMessageColor() {
        String expanded = expander(new ArrayList<>()).expand(
                "<HauntedMC:Current>value</HauntedMC> tail");

        assertEquals("<transition:#A855F7:#38BDF8:0.5>value tail", expanded);
        assertEquals("value tail", PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(expanded)));
    }

    private static ThemeTagExpander expander(List<String> warnings) {
        return new ThemeTagExpander(ThemeRegistry.of(List.of(HAUNTED)), warnings::add);
    }
}
