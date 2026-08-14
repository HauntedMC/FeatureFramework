package nl.hauntedmc.featureframework.localization;

import net.kyori.adventure.audience.Audience;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentLocalizationTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersPlatformAndNamedPlaceholdersAndCachesStaticPlayerMessages() {
        FrameworkLogger logger = FrameworkLogger.noop();
        LocalizationStore root = new LocalizationStore(
                getClass().getClassLoader(),
                new ConfigService(tempDir, logger, getClass().getClassLoader()),
                logger
        );
        TestLocalization localization = new TestLocalization(root.openFeature("Demo"), logger);
        MessageMap defaults = new MessageMap();
        defaults.add("welcome", "Hello %player% {target}");
        defaults.add("static", "Always the same");
        localization.registerDefaultMessages(defaults);
        Audience audience = Audience.empty();

        String rendered = ComponentFormatter.serialize(localization.getMessage("welcome")
                        .forAudience(audience)
                        .with("target", "friend")
                        .build())
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
        var first = localization.messagesFor(audience).build("static");
        var second = localization.messagesFor(audience).build("static");

        assertEquals("Hello Alex friend", rendered);
        assertSame(first, second);
    }

    private static final class TestLocalization extends ComponentLocalization {
        private TestLocalization(LocalizationStore store, FrameworkLogger logger) {
            super(store, logger, Audience.class, ignored -> Language.NL,
                    (message, ignored) -> message.replace("%player%", "Alex"), false);
        }
    }
}
