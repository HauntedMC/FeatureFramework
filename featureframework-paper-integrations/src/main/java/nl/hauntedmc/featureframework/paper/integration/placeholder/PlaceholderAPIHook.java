package nl.hauntedmc.featureframework.paper.integration.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe boundary around PlaceholderAPI and third-party expansions. */
public final class PlaceholderAPIHook {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("%([A-Za-z0-9]+)(?:_[^%]*)?%");
    private static final Pattern VAULT_ECONOMY_PATTERN =
            Pattern.compile("(?i)%vault_eco_[^%]+%");
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final int MAX_WARNING_KEYS = 2_048;
    private static final ConcurrentMap<WarningKey, Long> LAST_WARNINGS = new ConcurrentHashMap<>();

    private PlaceholderAPIHook() {
    }

    public static String applyPlaceholders(String text, Player player) {
        return applyPlaceholders(
                text,
                player,
                pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName),
                me.clip.placeholderapi.PlaceholderAPI::setPlaceholders,
                PlaceholderAPIHook::isVaultEconomyAvailable,
                PlaceholderAPIHook::logWarning,
                System::nanoTime
        );
    }

    static String applyPlaceholders(
            String text,
            Player player,
            Predicate<String> pluginEnabled,
            BiFunction<Player, String, String> resolver
    ) {
        return applyPlaceholders(
                text,
                player,
                pluginEnabled,
                resolver,
                () -> true,
                (message, failure) -> { },
                System::nanoTime
        );
    }

    static String applyPlaceholders(
            String text,
            Player player,
            Predicate<String> pluginEnabled,
            BiFunction<Player, String, String> resolver,
            BooleanSupplier vaultEconomyAvailable,
            BiConsumer<String, Throwable> warningSink,
            LongSupplier nanoTime
    ) {
        Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(vaultEconomyAvailable, "vaultEconomyAvailable");
        Objects.requireNonNull(warningSink, "warningSink");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (text == null || player == null || text.indexOf('%') < 0) {
            return text;
        }

        try {
            if (!pluginEnabled.test("PlaceholderAPI")) {
                return text;
            }
        } catch (RuntimeException | LinkageError failure) {
            warnRateLimited(text, player, failure, warningSink, nanoTime.getAsLong());
            return text;
        }

        MaskedText input = MaskedText.unchanged(text);
        if (VAULT_ECONOMY_PATTERN.matcher(text).find() && !safeEconomyAvailability(vaultEconomyAvailable)) {
            input = MaskedText.maskVaultEconomy(text);
            warnRateLimited(text, player, null, warningSink, nanoTime.getAsLong());
        }

        try {
            String resolved = resolver.apply(player, input.masked());
            if (resolved == null) {
                throw new IllegalStateException("PlaceholderAPI returned null.");
            }
            return input.restore(resolved);
        } catch (RuntimeException | LinkageError failure) {
            warnRateLimited(text, player, failure, warningSink, nanoTime.getAsLong());
            return text;
        }
    }

    private static boolean safeEconomyAvailability(BooleanSupplier availability) {
        try {
            return availability.getAsBoolean();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isVaultEconomyAvailable() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                return false;
            }
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    false,
                    PlaceholderAPIHook.class.getClassLoader()
            );
            RegisteredServiceProvider<?> registration = economyRegistration(economyClass);
            if (registration == null || registration.getProvider() == null
                    || registration.getPlugin() == null || !registration.getPlugin().isEnabled()) {
                return false;
            }
            Object provider = registration.getProvider();
            try {
                JavaPlugin providingPlugin = JavaPlugin.getProvidingPlugin(provider.getClass());
                if (!providingPlugin.isEnabled()) {
                    return false;
                }
            } catch (IllegalArgumentException ignored) {
                // Generated providers can use a bridge class loader; the service owner check above remains valid.
            }
            return providerReportsEnabled(provider);
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RegisteredServiceProvider<?> economyRegistration(Class<?> economyClass) {
        return Bukkit.getServicesManager().getRegistration((Class) economyClass);
    }

    private static boolean providerReportsEnabled(Object provider) {
        try {
            Method method = provider.getClass().getMethod("isEnabled");
            Object result = method.invoke(provider);
            return !(result instanceof Boolean enabled) || enabled;
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void warnRateLimited(
            String text,
            Player player,
            Throwable failure,
            BiConsumer<String, Throwable> warningSink,
            long now
    ) {
        WarningKey key = new WarningKey(
                playerKey(player),
                expansionKey(text),
                text.hashCode(),
                text.length()
        );
        AtomicBoolean emit = new AtomicBoolean();
        LAST_WARNINGS.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= WARNING_INTERVAL_NANOS) {
                emit.set(true);
                return now;
            }
            return previous;
        });
        trimWarningState(now);
        if (!emit.get()) {
            return;
        }

        String playerName = safePlayerName(player);
        String expansions = key.expansions();
        String message = failure == null
                ? "Vault economy placeholders were left unresolved for player '" + playerName
                        + "' because no enabled economy provider is available (expansions: " + expansions + ")."
                : "Placeholder resolution failed for player '" + playerName + "' (expansions: " + expansions
                        + "); keeping the original unresolved message.";
        warningSink.accept(message, failure);
    }

    private static void trimWarningState(long now) {
        if (LAST_WARNINGS.size() <= MAX_WARNING_KEYS) {
            return;
        }
        long staleBefore = now - WARNING_INTERVAL_NANOS * 2;
        LAST_WARNINGS.entrySet().removeIf(entry -> entry.getValue() < staleBefore);
        if (LAST_WARNINGS.size() <= MAX_WARNING_KEYS) {
            return;
        }
        int toRemove = LAST_WARNINGS.size() - MAX_WARNING_KEYS;
        for (WarningKey key : LAST_WARNINGS.keySet()) {
            if (toRemove-- <= 0) {
                break;
            }
            LAST_WARNINGS.remove(key);
        }
    }

    private static String expansionKey(String text) {
        Set<String> expansions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            expansions.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return expansions.isEmpty() ? "unknown" : String.join(",", expansions);
    }

    private static String playerKey(Player player) {
        try {
            UUID uniqueId = player.getUniqueId();
            if (uniqueId != null) {
                return uniqueId.toString();
            }
        } catch (RuntimeException ignored) {
            // Fall through to a stable best-effort key.
        }
        String name = safePlayerName(player);
        return name + "@" + Integer.toUnsignedString(System.identityHashCode(player));
    }

    private static String safePlayerName(Player player) {
        try {
            String name = player.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Use a non-identifying fallback.
        }
        return "unknown";
    }

    private static void logWarning(String message, Throwable failure) {
        if (failure == null) {
            Bukkit.getLogger().warning("[FeatureFramework] " + message);
        } else {
            Bukkit.getLogger().log(Level.WARNING, "[FeatureFramework] " + message, failure);
        }
    }

    static void clearWarningStateForTests() {
        LAST_WARNINGS.clear();
    }

    private record WarningKey(
            String player,
            String expansions,
            int messageHash,
            int messageLength
    ) {
    }

    private record MaskedText(String masked, Map<String, String> replacements) {
        private static MaskedText unchanged(String text) {
            return new MaskedText(text, Map.of());
        }

        private static MaskedText maskVaultEconomy(String text) {
            Matcher matcher = VAULT_ECONOMY_PATTERN.matcher(text);
            StringBuffer masked = new StringBuffer(text.length());
            Map<String, String> replacements = new LinkedHashMap<>();
            int index = 0;
            while (matcher.find()) {
                String token;
                do {
                    token = "__FEATUREFRAMEWORK_VAULT_ECONOMY_" + index++ + "_"
                            + Integer.toUnsignedString(text.hashCode()) + "__";
                } while (text.contains(token) || replacements.containsKey(token));
                replacements.put(token, matcher.group());
                matcher.appendReplacement(masked, Matcher.quoteReplacement(token));
            }
            matcher.appendTail(masked);
            return new MaskedText(masked.toString(), Map.copyOf(replacements));
        }

        private String restore(String resolved) {
            String restored = resolved;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                restored = restored.replace(replacement.getKey(), replacement.getValue());
            }
            return restored;
        }
    }
}
