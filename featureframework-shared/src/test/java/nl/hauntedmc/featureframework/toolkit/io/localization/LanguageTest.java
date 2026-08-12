package nl.hauntedmc.featureframework.toolkit.io.localization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {
    @Test
    void languageFileAndLocalizationContractsAreStable() {
        assertEquals("messages_NL.yml", Language.NL.getFileName());
        assertEquals("messages_EN.yml", Language.EN.getFileName());
        assertTrue(Language.NL.isLocalizable());
        assertEquals(List.of(Language.NL, Language.EN), Language.localizableValues());
    }
}
