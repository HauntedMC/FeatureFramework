package nl.hauntedmc.featureframework.toolkit.text.format.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyColorUtilsTest {

    @Test
    void convertsAmpAndSectionCodesToMiniMessageTags() {
        assertEquals("<green>Hi<reset>", LegacyColorUtils.convertAmpCodesToMini("&aHi&r"));
        assertEquals("<red>Hi<italic>", LegacyColorUtils.convertSecCodesToMini("§cHi§o"));
    }

    @Test
    void nearestNamedLegacyCodeFindsExpectedEndpoints() {
        assertEquals('0', LegacyColorUtils.nearestNamedLegacyCode("000000"));
        assertEquals('f', LegacyColorUtils.nearestNamedLegacyCode("FFFFFF"));
    }

    @Test
    void tagAliasMapContainsKnownAliases() {
        assertEquals('7', LegacyColorUtils.TAG_TO_CODE.get("grey"));
        assertEquals('d', LegacyColorUtils.TAG_TO_CODE.get("pink"));
        assertTrue(LegacyColorUtils.TAG_TO_CODE.containsKey("underlined"));
    }
}
