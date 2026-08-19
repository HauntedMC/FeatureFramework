package nl.hauntedmc.featureframework.toolkit.io.localization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {
    @Test
    void languageFileAndLocalizationContractsAreStable() {
        assertEquals("NL", Language.NL.code());
        assertEquals("messages_NL.yml", Language.NL.getFileName());
        assertEquals("messages_EN.yml", Language.EN.getFileName());
        assertTrue(Language.NL.isLocalizable());
        assertEquals(Language.NL, Language.fromCode(" nl ").orElseThrow());
        assertTrue(Language.fromCode("unknown").isEmpty());
        assertTrue(Language.fromCode(null).isEmpty());
        List<Language> localizable = Language.localizableValues();
        assertEquals(List.of(Language.NL, Language.EN), localizable);
        assertSame(localizable, Language.localizableValues());
    }
}
