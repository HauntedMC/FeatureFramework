package nl.hauntedmc.featureframework.toolkit.text.format;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {

    @Test
    void convenienceMethodsAndStrippersWorkForMixedInput() {
        assertEquals("<green>Hello>", TextFormatter.toMiniMessage("&aHello>"));
        assertTrue(TextFormatter.toLegacyAmpersand("<red>Hello").contains("&c"));
        assertTrue(TextFormatter.toLegacySection("<red>Hello").contains("§c"));
        assertEquals("Hello", TextFormatter.toPlain("&aHello"));

        assertEquals("Hello", TextFormatter.stripLegacyCodes("&aHello"));
        assertEquals("Hello", TextFormatter.stripMiniMessageTags("<bold>Hello</bold>"));
        assertEquals("<tag>", TextFormatter.escapeForMiniMessage("<tag>"));
        assertNull(TextFormatter.escapeForMiniMessage(null));
    }

    @Test
    void converterSupportsExpectPreprocessAndOptionMutations() {
        String mini = TextFormatter.convert("§x§1§2§3§4§5§6 Hello")
                .expect(Set.of(TextFormatter.InputFormat.HEX_BUNGEE_SECTION, TextFormatter.InputFormat.LEGACY_SECTION))
                .options(b -> b.normalizeSectionToAmpersand(false))
                .preprocess(s -> s.replace("Hello", "World"))
                .toMiniMessage();
        assertTrue(mini.contains("<#123456>"));
        assertTrue(mini.contains("World"));

        String miniDouble = TextFormatter.convert("<##A1B2C3>ok")
                .expect(TextFormatter.InputFormat.HEX_MINI_DOUBLE)
                .toMiniMessage();
        assertEquals("<#A1B2C3>ok", miniDouble);
    }

    @Test
    void miniMessageToLegacyRespectsHexAndResetOptions() {
        TextFormatter.Options opts = TextFormatter.Options.builder()
                .legacyOutputHexColors(true)
                .xRepeatedHex(true)
                .legacyEmitResetOnClose(true)
                .build();

        String legacy = TextFormatter.convert("<#A1B2C3><bold>X</bold>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .options(opts)
                .toLegacy('&');
        assertTrue(legacy.contains("&x&A&1&B&2&C&3"));
        assertTrue(legacy.contains("&lX&r"));

        String downsampled = TextFormatter.convert("<#FFFFFF>Y")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .options(b -> b.legacyOutputHexColors(false))
                .toLegacy('&');
        assertTrue(downsampled.contains("&fY"));
    }

    @Test
    void toPlainRemovesMiniAndLegacyArtifacts() {
        String plain = TextFormatter.convert("<bold>&aHello</bold>")
                .expect(TextFormatter.InputFormat.ANY)
                .toPlain();
        assertEquals("Hello", plain);
    }

    @Test
    void converterDefaultAndSectionSpecificBranchesAreCovered() {
        assertEquals("", TextFormatter.convert(null).toMiniMessage());
        assertEquals("", TextFormatter.convert("").expect(TextFormatter.InputFormat.ANY).toMiniMessage());

        String passthrough = TextFormatter.convert("<bold>Hello</bold>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .toMiniMessage();
        assertEquals("<bold>Hello</bold>", passthrough);

        String normalizedSection = TextFormatter.convert("§aHello")
                .expect(TextFormatter.InputFormat.LEGACY_SECTION)
                .toMiniMessage();
        assertEquals("&aHello", normalizedSection);

        String sectionPound = TextFormatter.convert("§#A1B2C3Hello")
                .expect(TextFormatter.InputFormat.HEX_POUND)
                .options(b -> b.normalizeSectionToAmpersand(false))
                .toMiniMessage();
        assertTrue(sectionPound.contains("<#A1B2C3>Hello"));

        String sectionLegacyNoNormalization = TextFormatter.convert("§cHello")
                .expect(TextFormatter.InputFormat.LEGACY_SECTION)
                .options(b -> b.normalizeSectionToAmpersand(false))
                .toMiniMessage();
        assertEquals("<red>Hello", sectionLegacyNoNormalization);

        String ampHex = TextFormatter.convert("&x&1&2&3&4&5&6Hex")
                .expect(TextFormatter.InputFormat.HEX_BUNGEE_AMP)
                .toMiniMessage();
        assertTrue(ampHex.contains("<#123456>Hex"));
    }

    @Test
    void legacySerializationAndStripperEdgeCasesAreCovered() {
        assertEquals("", TextFormatter.convert("").expect(TextFormatter.InputFormat.MINIMESSAGE).toLegacy('&'));

        String normalizedDoubleHex = TextFormatter.convert("<##A1B2C3>Hello")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .toLegacy('&');
        assertTrue(normalizedDoubleHex.contains("&#A1B2C3"));

        String explicitHex = TextFormatter.convert("<#A1B2C3>Hello")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .options(b -> b.legacyOutputHexColors(true).xRepeatedHex(false))
                .toLegacy('&');
        assertTrue(explicitHex.contains("&#A1B2C3"));

        assertNull(TextFormatter.stripLegacyCodes(null));
        assertEquals("", TextFormatter.stripLegacyCodes(""));
        String strippedLegacy = TextFormatter.stripLegacyCodes("§x§1§2§3§4§5§6 §#A1B2C3 §aHello");
        assertFalse(strippedLegacy.contains("§"));
        assertFalse(strippedLegacy.contains("#A1B2C3"));
        assertEquals("  Hello", strippedLegacy);

        assertNull(TextFormatter.stripMiniMessageTags(null));
        assertEquals("", TextFormatter.stripMiniMessageTags(""));
        assertEquals("Hello", TextFormatter.stripMiniMessageTags("<#A1B2C3>Hello"));
    }
}
