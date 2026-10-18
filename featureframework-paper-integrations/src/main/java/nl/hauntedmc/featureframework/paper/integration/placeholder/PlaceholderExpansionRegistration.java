package nl.hauntedmc.featureframework.paper.integration.placeholder;

import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns an optional expansion without exposing PlaceholderAPI types to feature lifecycles. */
public final class PlaceholderExpansionRegistration implements AutoCloseable {
    private final String identifier;
    private final Runnable unregister;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PlaceholderExpansionRegistration(String identifier, Runnable unregister) {
        this.identifier = identifier;
        this.unregister = unregister;
    }

    /**
     * Constructs and registers an expansion only when PlaceholderAPI is enabled.
     * The factory must lazily create a PlaceholderExpansion; use a lambda rather than constructing it first.
     * Failures return an inactive handle and leave the owning feature operational.
     */
    public static PlaceholderExpansionRegistration register(String identifier, Supplier<?> factory) {
        return register(identifier, factory, name -> Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? AvailableExpansion.current(name) : null);
    }

    static PlaceholderExpansionRegistration register(
            String identifier, Supplier<?> factory, Function<String, ?> registeredExpansion
    ) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(registeredExpansion, "registeredExpansion");
        Runnable cleanup = null;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                cleanup = AvailableExpansion.install(identifier, factory, registeredExpansion);
            }
        } catch (RuntimeException | LinkageError failure) {
            PlaceholderAPIHook.logWarning("Could not register PlaceholderAPI expansion '" + identifier
                    + "'; the feature will continue without its placeholders.", failure);
        }
        return new PlaceholderExpansionRegistration(identifier, cleanup);
    }

    /** Best-effort cleanup, safe to call repeatedly even after PlaceholderAPI is disabled or removed. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || unregister == null) {
            return;
        }
        try {
            unregister.run();
        } catch (RuntimeException | LinkageError failure) {
            PlaceholderAPIHook.logWarning("Could not unregister PlaceholderAPI expansion '" + identifier
                    + "'; continuing feature cleanup.", failure);
        }
    }

    /** Loaded only inside the guarded registration path; the outer handle has no PAPI signatures. */
    private static final class AvailableExpansion {
        private static Runnable install(String identifier, Supplier<?> factory, Function<String, ?> registeredExpansion) {
            var expansion = (me.clip.placeholderapi.expansion.PlaceholderExpansion)
                    Objects.requireNonNull(factory.get(), "expansion");
            try {
                if (expansion.register()) {
                    return () -> remove(expansion, registeredExpansion);
                }
            } catch (RuntimeException | LinkageError failure) {
                try {
                    remove(expansion, registeredExpansion);
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
            PlaceholderAPIHook.logWarning("Could not register PlaceholderAPI expansion '" + identifier
                    + "'; registration was refused. The feature will continue without its placeholders.", null);
            return null;
        }

        private static Object current(String identifier) {
            return me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance().getLocalExpansionManager()
                    .findExpansionByIdentifier(identifier).orElse(null);
        }

        private static void remove(
                me.clip.placeholderapi.expansion.PlaceholderExpansion expansion, Function<String, ?> registeredExpansion
        ) {
            // A replacement may already own this identifier after a PAPI reload.
            Object registered = registeredExpansion.apply(expansion.getIdentifier());
            if (registered == expansion && !expansion.unregister()) {
                PlaceholderAPIHook.logWarning("PlaceholderAPI refused to unregister expansion '"
                        + expansion.getIdentifier() + "'; continuing feature cleanup.", null);
            }
        }
    }
}
