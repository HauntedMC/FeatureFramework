package nl.hauntedmc.featureframework.toolkit.text;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextPatternsTest {

    @Test
    void regexAndFormatConstantsMatchExpectedShapes() {
        assertTrue(TextPatterns.AMP_CODES.matcher("&aHello").find());
        assertTrue(TextPatterns.SEC_CODES.matcher("§bHello").find());
        assertTrue(TextPatterns.POUND_HEX.matcher("&#A1B2C3").find());
        assertTrue(TextPatterns.SECTION_POUND_HEX.matcher("§#A1B2C3").find());
        assertTrue(TextPatterns.AMP_BUNGEE_HEX.matcher("&x&1&2&3&4&5&6").find());
        assertTrue(TextPatterns.SEC_BUNGEE_HEX.matcher("§x§1§2§3§4§5§6").find());
        assertTrue(TextPatterns.MINI_HEX_TAG.matcher("<#A1B2C3>").find());
        assertTrue(TextPatterns.MINI_HEX_DOUBLE.matcher("<##A1B2C3>").find());
        assertTrue(TextPatterns.ANY_MINI_TAG.matcher("<bold>x</bold>").find());
        assertTrue(TextPatterns.URL.matcher("visit www.example.com").find());
        assertTrue(TextPatterns.MC_NAME.matcher("Player_01").matches());
        assertTrue(TextPatterns.BUKKIT_ALIAS_FORMAT.matcher("cmd_name-1").matches());
        assertTrue(TextPatterns.MC_IN_VERSION.matcher("(MC: 1.21.1)").find());
        assertTrue(TextPatterns.DATE_IN_NAME.matcher("backup-20-03-2026.yml").matches());
        assertEquals("20-03-2026", TextPatterns.DATE_FMT.format(LocalDate.of(2026, 3, 20)));
        assertEquals("20-03-2026_104500",
                TextPatterns.TS_FMT.format(LocalDateTime.of(2026, 3, 20, 10, 45)));
    }
}
