package nl.hauntedmc.featureframework.paper.integration.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import nl.hauntedmc.featureframework.paper.localization.PaperLocalization;
import nl.hauntedmc.featureframework.paper.localization.PaperMessageDecorator;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithoutPapiTest {
    private static final String PACKAGE = "nl.hauntedmc.featureframework.paper.integration.placeholder.";

    @TempDir
    Path directory;

    @Test
    void publicHookDecoratorAndLocalizationWorkWhenPapiClassesAreNotLoadable() throws Exception {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getLogger).thenReturn(mock(Logger.class));
            // Also cover a plugin reported enabled whose API is missing or inaccessible.
            for (boolean enabled : new boolean[]{false, true}) {
                when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(enabled);
                ClassLoader loader = withoutPapi();
                assertThrows(ClassNotFoundException.class,
                        () -> loader.loadClass("me.clip.placeholderapi.PlaceholderAPI"));
                Class<?> hook = loader.loadClass(PACKAGE + "PlaceholderAPIHook");
                var apply = hook.getMethod("applyPlaceholders", String.class, Player.class);
                assertNull(apply.invoke(null, null, player));
                assertEquals("plain", apply.invoke(null, "plain", player));
                assertEquals("%player_name%", apply.invoke(null, "%player_name%", null));
                assertEquals("%player_name%", apply.invoke(null, "%player_name%", player));
                assertEquals("%player_name%", apply.invoke(null, "%player_name%", player));

                var decorator = (PaperMessageDecorator) loader.loadClass(PACKAGE + "PlaceholderMessageDecorator")
                        .getMethod("create").invoke(null);
                assertEquals("%player_name%", decorator.decorate("%player_name%", player));
                PaperLocalization localization = new PaperLocalization(plugin,
                        new ConfigService(directory, FrameworkLogger.noop(), getClass().getClassLoader()),
                        ignored -> Language.NL, decorator).openFeature("WithoutPapi" + enabled);
                MessageMap defaults = new MessageMap();
                defaults.add("welcome", "<red>Hello {target} %player_name%");
                localization.registerDefaultMessages(defaults);
                var message = localization.getMessage("welcome").forAudience(player).with("target", "friend").build();
                assertEquals("Hello friend %player_name%", ComponentFormatter.serialize(message)
                        .format(ComponentFormatter.Serializer.Format.PLAIN).build());
                assertTrue(ComponentFormatter.serialize(message)
                        .format(ComponentFormatter.Serializer.Format.MINIMESSAGE).build().contains("<red>"));

                var register = loader.loadClass(PACKAGE + "PlaceholderExpansionRegistration")
                        .getMethod("register", String.class, Supplier.class);
                Supplier<Object> factory = () -> {
                    if (!enabled) fail("An absent PAPI must not construct expansions");
                    throw new NoClassDefFoundError("me/clip/placeholderapi/expansion/PlaceholderExpansion");
                };
                AutoCloseable handle = (AutoCloseable) register.invoke(null, "test", factory);
                assertDoesNotThrow(handle::close);
                assertDoesNotThrow(handle::close);
            }
        }
    }

    @Test
    void publicHookStillUsesInstalledPapi() {
        PluginManager plugins = mock(PluginManager.class);
        Player player = mock(Player.class);
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlaceholderAPI> papi = mockStatic(PlaceholderAPI.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            papi.when(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%")).thenReturn("Alice");
            assertEquals("Alice", PlaceholderAPIHook.applyPlaceholders("%player_name%", player));
            papi.verify(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"));
        }
    }

    private static ClassLoader withoutPapi() {
        return new ClassLoader(WithoutPapiTest.class.getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("me.clip.placeholderapi.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(PACKAGE)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
    }
}
