package nl.hauntedmc.featureframework.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureCommandViewTest {

    @Test
    void renderCsvColoredHandlesEmptyAndJoinedValues() {
        String none = plain(FeatureCommandView.renderCsvColored(List.of(), NamedTextColor.WHITE, NamedTextColor.GRAY, true));
        String noNone = plain(FeatureCommandView.renderCsvColored(List.of(), NamedTextColor.WHITE, NamedTextColor.GRAY, false));
        String values = plain(FeatureCommandView.renderCsvColored(List.of("a", "b", "c"), NamedTextColor.WHITE, NamedTextColor.GRAY, true));

        assertEquals("none", none);
        assertEquals("", noNone);
        assertEquals("a, b, c", values);
    }

    @Test
    void buildFeatureInfoMessageRendersExpectedSections() {
        Component message = FeatureCommandView.buildFeatureInfoMessage(
                "ChatFilter",
                true,
                "2.0.1",
                List.of("Vault", "PlaceholderAPI"),
                List.of("Core")
        );

        String plain = plain(message);
        assertTrue(plain.contains("Feature: ChatFilter"));
        assertTrue(plain.contains("Status: enabled"));
        assertTrue(plain.contains("Version: v2.0.1"));
        assertTrue(plain.contains("Plugin deps: Vault, PlaceholderAPI"));
        assertTrue(plain.contains("Feature deps: Core"));
    }

    @Test
    void buildLoadedFeaturesOneLineSupportsVersionToggle() {
        List<FeatureCommandModel.FeatureListEntry> entries = List.of(
                new FeatureCommandModel.FeatureListEntry("A", "1"),
                new FeatureCommandModel.FeatureListEntry("B", "2")
        );

        String withoutVersion = plain(FeatureCommandView.buildLoadedFeaturesOneLine(entries, false));
        String withVersion = plain(FeatureCommandView.buildLoadedFeaturesOneLine(entries, true));

        assertEquals("Enabled Features (2): A, B", withoutVersion);
        assertEquals("Enabled Features (2): A (v1), B (v2)", withVersion);
    }

    private static String plain(Component component) {
        return ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
    }
}
