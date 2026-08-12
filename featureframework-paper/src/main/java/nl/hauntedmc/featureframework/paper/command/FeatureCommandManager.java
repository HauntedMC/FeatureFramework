package nl.hauntedmc.featureframework.paper.command;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierCommand;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.text.TextPatterns;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Registers feature commands while coordinating plugin-wide label ownership and reversible external takeovers.
 */
public class FeatureCommandManager {

    private final Plugin plugin;
    private final BrigadierDispatcher dispatcher;
    private final BooleanSupplier overwriteConflicts;
    private final FrameworkLogger logger;
    private final CommandLabelOwnership ownership;
    private final CommandRegistryTakeover registryTakeover;
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredBrigadierLabels = new ConcurrentHashMap<>();
    private final Map<String, CommandRegistryTakeover.Takeover> brigadierTakeovers = new ConcurrentHashMap<>();
    private volatile FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureCommandManager(
            Plugin plugin,
            BrigadierDispatcher dispatcher,
            CommandLabelOwnership ownership,
            BooleanSupplier overwriteConflicts,
            FrameworkLogger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.overwriteConflicts = Objects.requireNonNull(overwriteConflicts, "overwriteConflicts");
        this.logger = Objects.requireNonNull(logger, "logger");
        CommandMap commandMap = plugin.getServer().getCommandMap();
        this.registryTakeover = new CommandRegistryTakeover(commandMap, dispatcher);
    }

    public void registerBrigadierCommand(@NotNull BrigadierCommand command) {
        requireOpen();
        runOnMain(() -> doRegisterBrigadier(command));
    }

    private void doRegisterBrigadier(BrigadierCommand command) {
        requireOpen();
        String name = validateLabel(command.name(), "command name");
        if (registeredBrigadierCommands.containsKey(name)) {
            logger.warn("[Brigadier] Already registered by this feature: " + name);
            return;
        }
        List<String> aliases = sanitizeAliases(command.aliases(), name);
        List<String> labels = commandLabels(name, aliases);
        String collision = ownership.claim(command, labels);
        if (collision != null) {
            logger.warn("[Brigadier] Root label '" + collision
                    + "' is already owned by another framework feature; skipping /" + name + ".");
            return;
        }

        CommandRegistryTakeover.Claim claim;
        try {
            claim = registryTakeover.claim(labels, overwriteConflicts.getAsBoolean());
        } catch (Throwable throwable) {
            ownership.release(command, labels);
            logger.warn("[Brigadier] Failed to prepare labels for /" + name
                    + ": " + throwable.getMessage());
            return;
        }
        if (!claim.claimed()) {
            ownership.release(command, labels);
            logBlockedConflict(name, claim.blockingConflict());
            return;
        }
        logTakeovers(claim.conflicts(), name);

        boolean registered = false;
        try {
            if (!dispatcher.attachBrigadierCommand(command, name, aliases)) {
                logger.warn("[Brigadier] Could not attach /" + name
                        + " after its labels were prepared.");
                return;
            }
            registeredBrigadierCommands.put(name, command);
            registeredBrigadierLabels.put(name, labels);
            brigadierTakeovers.put(name, claim.takeover());
            registered = true;
            logger.info("[Brigadier] Registered /" + name
                    + " (" + aliases.size() + " aliases)");
        } catch (Throwable throwable) {
            logger.warn("[Brigadier] Attach failed for /" + name
                    + ": " + throwable.getMessage());
        } finally {
            if (!registered) {
                restoreTakeover(claim.takeover(), name);
                ownership.release(command, labels);
            }
        }
    }

    public void unregisterBrigadierCommand(@NotNull String commandName) {
        runOnMain(() -> doUnregisterBrigadier(commandName));
    }

    private void doUnregisterBrigadier(String commandName) {
        String name = normalize(commandName);
        BrigadierCommand command = registeredBrigadierCommands.remove(name);
        if (command == null) {
            logger.warn("[Brigadier] Not registered: " + name);
            return;
        }
        List<String> labels = registeredBrigadierLabels.remove(name);
        if (labels == null) {
            labels = commandLabels(name, sanitizeAliases(command.aliases(), name));
        }
        try {
            dispatcher.detachBrigadierCommand(command, labels);
        } catch (Throwable throwable) {
            logger.warn("[Brigadier] Detach failed for /" + name
                    + ": " + throwable.getMessage());
        } finally {
            ownership.release(command, labels);
            restoreTakeover(brigadierTakeovers.remove(name), name);
        }
    }

    public void unregisterAllBrigadierCommands() {
        quiesce();
        List<String> names = new ArrayList<>(registeredBrigadierCommands.keySet());
        runOnMain(() -> {
            names.forEach(this::doUnregisterBrigadier);
            closeIfEmpty();
        });
    }

    public void quiesce() {
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
        }
    }

    public FeatureResourceState state() {
        return state;
    }

    private void closeIfEmpty() {
        if (registeredBrigadierCommands.isEmpty()) {
            state = FeatureResourceState.CLOSED;
        }
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Command manager is " + state);
        }
    }

    public int getTotalRegisteredCommandCount() {
        return registeredBrigadierCommands.size();
    }

    public Set<String> getAllRegisteredCommandNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registeredBrigadierCommands.keySet()));
    }

    /**
     * Returns whether a command label is currently claimed by any framework feature.
     */
    public boolean isLabelOwnedByFramework(String label) {
        return ownership.isClaimed(label);
    }

    public Map<String, BrigadierCommand> getRegisteredBrigadierCommands() {
        return Map.copyOf(registeredBrigadierCommands);
    }

    public int getRegisteredBrigadierCommandCount() {
        return registeredBrigadierCommands.size();
    }

    private void logBlockedConflict(
            String commandName,
            CommandRegistryTakeover.Conflict conflict
    ) {
        logger.warn("[Brigadier] Command label '/" + conflict.label() + "' is already owned by "
                + conflict.ownerDescription() + "; skipping /" + commandName
                + " because global.commands.overwrite-conflicts is false.");
    }

    private void logTakeovers(
            List<CommandRegistryTakeover.Conflict> conflicts,
            String commandName
    ) {
        for (CommandRegistryTakeover.Conflict conflict : conflicts) {
            logger.warn("[Brigadier] Replacing command label '/" + conflict.label() + "' from "
                    + conflict.ownerDescription() + " while registering /" + commandName + ".");
        }
    }

    private void restoreTakeover(CommandRegistryTakeover.Takeover takeover, String commandName) {
        if (takeover == null || takeover.isEmpty()) {
            return;
        }
        try {
            CommandRegistryTakeover.RestoreResult result = registryTakeover.restore(takeover);
            if (!result.skippedBukkitLabels().isEmpty()) {
                logger.warn("Could not restore displaced Bukkit labels after unregistering /"
                        + commandName + ": " + String.join(", ", result.skippedBukkitLabels()));
            }
            if (!result.skippedBrigadierLabels().isEmpty()) {
                logger.warn("Could not restore displaced Brigadier roots after unregistering /"
                        + commandName + ": " + String.join(", ", result.skippedBrigadierLabels()));
            }
        } catch (Throwable throwable) {
            logger.warn("Failed to restore displaced command labels after unregistering /"
                    + commandName + ": " + throwable.getMessage());
        }
    }

    private static List<String> commandLabels(String name, Collection<String> aliases) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        labels.add(normalize(name));
        for (String alias : aliases) {
            labels.add(normalize(alias));
        }
        return List.copyOf(labels);
    }

    private static List<String> sanitizeAliases(Collection<String> aliases, String commandName) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            String normalized = validateLabel(alias, "command alias");
            if (!normalized.equals(commandName)) {
                sanitized.putIfAbsent(normalized, normalized);
            }
        }
        return List.copyOf(sanitized.values());
    }

    private static String validateLabel(String label, String description) {
        String normalized = normalize(label);
        if (!TextPatterns.BUKKIT_ALIAS_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + description + ": " + label);
        }
        return normalized;
    }

    private static String normalize(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }

    private void runOnMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }
}
