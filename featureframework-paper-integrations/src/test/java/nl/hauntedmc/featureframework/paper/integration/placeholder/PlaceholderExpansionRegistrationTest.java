package nl.hauntedmc.featureframework.paper.integration.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderExpansionRegistrationTest {
    @Test
    void skipsTheFactoryWhenPapiIsAbsentOrDisabled() {
        PluginManager plugins = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            @SuppressWarnings("unchecked")
            Supplier<Object> factory = mock(Supplier.class);
            var registration = PlaceholderExpansionRegistration.register("test", factory);
            assertDoesNotThrow(registration::close);
            assertDoesNotThrow(registration::close);
            verifyNoInteractions(factory);
        }
    }

    @Test
    void registersAndUnregistersAnOwnedExpansionOnce() {
        PlaceholderExpansion expansion = ownedExpansion();
        when(expansion.register()).thenReturn(true);
        when(expansion.unregister()).thenReturn(true);
        withPapi(() -> {
            var registration = PlaceholderExpansionRegistration.register("test", () -> expansion, name -> expansion);
            registration.close();
            registration.close();
            verify(expansion).register();
            verify(expansion).unregister();
        });
    }

    @Test
    void refusedRegistrationDoesNotRemoveAnotherExpansion() {
        PlaceholderExpansion expansion = mock(PlaceholderExpansion.class);
        withPapi(() -> {
            var registration = PlaceholderExpansionRegistration.register("test", () -> expansion);
            registration.close();
            verify(expansion).register();
            verify(expansion, never()).unregister();
        });
    }

    @Test
    void constructionAndRegistrationFailuresDoNotInterruptFeatureStartup() {
        for (Throwable failure : List.of(new IllegalStateException("broken expansion"),
                new NoClassDefFoundError("PAPI"), new NoSuchMethodError("PAPI"))) {
            withPapi(() -> {
                var unavailable = PlaceholderExpansionRegistration.register("test", () -> {
                    throwUnchecked(failure);
                    return null;
                });
                assertDoesNotThrow(unavailable::close);
                PlaceholderExpansion expansion = ownedExpansion();
                when(expansion.register()).thenAnswer(invocation -> { throwUnchecked(failure); return false; });
                when(expansion.unregister()).thenReturn(true);
                var broken = PlaceholderExpansionRegistration.register("test", () -> expansion, name -> expansion);
                assertDoesNotThrow(broken::close);
                verify(expansion).unregister();
            });
        }
    }

    @Test
    void cleanupFailureDoesNotStopOtherFeatureCleanupOrRetryTheSameHandle() {
        for (Throwable failure : List.of(new IllegalStateException("PAPI disabled"),
                new NoClassDefFoundError("PAPI removed"))) {
            PlaceholderExpansion expansion = ownedExpansion();
            when(expansion.register()).thenReturn(true);
            when(expansion.unregister()).thenAnswer(invocation -> { throwUnchecked(failure); return false; });
            withPapi(() -> {
                var registration = PlaceholderExpansionRegistration.register("test", () -> expansion, name -> expansion);
                assertDoesNotThrow(registration::close);
                assertDoesNotThrow(registration::close);
                verify(expansion).unregister();
            });
        }
    }

    @Test
    void doesNotUnregisterAReplacementAfterPapiReload() {
        PlaceholderExpansion expansion = ownedExpansion();
        when(expansion.register()).thenReturn(true);
        withPapi(() -> {
            AtomicReference<Object> current = new AtomicReference<>(expansion);
            var registration = PlaceholderExpansionRegistration.register("test", () -> expansion, name -> current.get());
            // PAPI's isRegistered() may still report true for a replacement with identical metadata.
            when(expansion.isRegistered()).thenReturn(true);
            current.set(mock(PlaceholderExpansion.class));
            registration.close();
            verify(expansion, never()).unregister();
        });
    }

    @Test
    void lookupAndLoggingFailuresAreAlsoContained() {
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenThrow(new NoClassDefFoundError("plugin manager"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getLogger).thenThrow(new IllegalStateException("logger"));
            assertDoesNotThrow(() -> PlaceholderExpansionRegistration.register("test", () -> {
                fail("Factory must not run after lookup failed");
                return null;
            }).close());
        }
    }

    private static PlaceholderExpansion ownedExpansion() {
        PlaceholderExpansion expansion = mock(PlaceholderExpansion.class);
        when(expansion.getIdentifier()).thenReturn("test");
        return expansion;
    }

    private static void withPapi(Runnable test) {
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getLogger).thenReturn(mock(Logger.class));
            test.run();
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        throw (Error) failure;
    }
}
