package nl.hauntedmc.featureframework.paper.integration.placeholder;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceholderAPIHookTest {
    private final Player player = mock(Player.class);

    @BeforeEach
    void resetWarnings() {
        PlaceholderAPIHook.clearWarningStateForTests();
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(player.getName()).thenReturn("Alice");
    }

    @Test
    void skipsUnavailablePapiAndInputsWithoutPlayerPlaceholders() {
        Predicate<String> unavailable = name -> false;
        BiFunction<Player, String, String> resolver = (context, message) -> {
            fail("Unavailable PAPI must not be called");
            return message;
        };
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, unavailable, resolver));
        Predicate<String> unexpectedLookup = name -> {
            fail("Plain messages and missing context must not look up PAPI");
            return true;
        };
        assertNull(PlaceholderAPIHook.applyPlaceholders(null, player, unexpectedLookup, resolver));
        assertEquals("Hello", PlaceholderAPIHook.applyPlaceholders("Hello", player, unexpectedLookup, resolver));
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", null, unexpectedLookup, resolver));
    }

    @Test
    void containsRuntimeAndLinkageFailuresFromLookupAndResolution() {
        for (Throwable failure : List.of(new IllegalStateException("broken expansion"),
                new NoClassDefFoundError("missing PAPI"), new NoSuchMethodError("incompatible PAPI"),
                new ExceptionInInitializerError("broken initialization"))) {
            assertEquals("Hello %player_name%", PlaceholderAPIHook.applyPlaceholders(
                    "Hello %player_name%", player, name -> true,
                    (context, message) -> { throwUnchecked(failure); return message; }));
            assertEquals("Hello %player_name%", PlaceholderAPIHook.applyPlaceholders(
                    "Hello %player_name%", player,
                    name -> { throwUnchecked(failure); return true; }, (context, message) -> "unexpected"));
        }
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, (context, message) -> null));
    }

    @Test
    void retriesAfterFailureAndRechecksAvailabilityOnEveryCall() {
        AtomicInteger attempts = new AtomicInteger();
        BiFunction<Player, String, String> resolver = (context, message) -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("not ready");
            return message.replace("%player_name%", "Alice");
        };
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, resolver));
        assertEquals("Alice", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, resolver));
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> false, resolver));
        assertEquals(2, attempts.get());
    }

    @Test
    void preservesUnavailableVaultTokensWhileResolvingOtherExpansions() {
        String text = "%vault_eco_balance% / %player_name%";
        BiFunction<Player, String, String> resolver = (context, message) -> {
            assertFalse(message.contains("%vault_eco_balance%"));
            return message.replace("%player_name%", "Alice");
        };
        assertEquals("%vault_eco_balance% / Alice", PlaceholderAPIHook.applyPlaceholders(
                text, player, name -> true, resolver, () -> false, (message, failure) -> { }, () -> 0L));
        assertEquals("%vault_eco_balance% / Alice", PlaceholderAPIHook.applyPlaceholders(
                text, player, name -> true, resolver, () -> { throw new NoClassDefFoundError("Vault"); },
                (message, failure) -> { }, () -> 0L));
        assertEquals("10 / Alice", PlaceholderAPIHook.applyPlaceholders(
                text, player, name -> true,
                (context, message) -> message.replace("%vault_eco_balance%", "10").replace("%player_name%", "Alice"),
                () -> true, (message, failure) -> { }, () -> 0L));
    }

    @Test
    void throttlesWarningsAndContainsDiagnosticFailures() {
        AtomicInteger warnings = new AtomicInteger();
        AtomicLong time = new AtomicLong();
        BiFunction<Player, String, String> broken = (context, message) -> {
            throw new IllegalStateException("broken expansion");
        };
        BiConsumer<String, Throwable> sink = (message, failure) -> warnings.incrementAndGet();
        for (int i = 0; i < 2; i++) {
            assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                    "%player_name%", player, name -> true, broken, () -> true, sink, time::get));
        }
        assertEquals(1, warnings.get());
        time.set(TimeUnit.MINUTES.toNanos(5));
        PlaceholderAPIHook.applyPlaceholders("%player_name%", player, name -> true, broken,
                () -> true, sink, time::get);
        assertEquals(2, warnings.get());

        PlaceholderAPIHook.clearWarningStateForTests();
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, broken, () -> true,
                (message, failure) -> { throw new NoClassDefFoundError("logger"); }, time::get));
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, broken, () -> true, sink,
                () -> { throw new IllegalStateException("clock"); }));
        when(player.getUniqueId()).thenThrow(new NoClassDefFoundError("player diagnostic"));
        assertEquals("%player_name%", PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, broken, () -> true, sink, time::get));
    }

    @Test
    void doesNotSuppressFatalVmFailures() {
        assertThrows(OutOfMemoryError.class, () -> PlaceholderAPIHook.applyPlaceholders(
                "%player_name%", player, name -> true, (context, message) -> { throw new OutOfMemoryError(); }));
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        throw (Error) failure;
    }
}
