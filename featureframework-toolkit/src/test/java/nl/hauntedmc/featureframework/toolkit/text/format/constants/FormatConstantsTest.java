package nl.hauntedmc.featureframework.toolkit.text.format.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatConstantsTest {

    @Test
    void constantsMatchExpectedTokens() {
        assertEquals('&', FormatConstants.AMP_CHAR);
        assertEquals('§', FormatConstants.SECTION_CHAR);
        assertEquals('#', FormatConstants.POUND_CHAR);
        assertEquals('<', FormatConstants.MINI_TAG_OPEN);
        assertEquals('>', FormatConstants.MINI_TAG_CLOSE);
        assertEquals("end", FormatConstants.END_TOKEN);
    }
}
