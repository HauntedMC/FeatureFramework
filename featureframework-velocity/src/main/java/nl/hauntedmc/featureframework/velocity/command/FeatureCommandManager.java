package nl.hauntedmc.featureframework.velocity.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.velocity.command.brigadier.BrigadierCommand;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Registers and unregisters all Velocity commands owned by one feature. */
public class FeatureCommandManager {
    private final Object plugin;
    private final CommandManager commandManager;
    private final CommandOwnershipRegistry ownershipRegistry;
    private final Logger logger;
    private final Map<String, BrigadierCommand> commands = new LinkedHashMap<>();
    private final Map<String, CommandMeta> metas = new LinkedHashMap<>();
    private final Map<String, CommandOwnershipRegistry.Registration> ownership = new LinkedHashMap<>();
    private String featureName;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public FeatureCommandManager(
            Object plugin,
            CommandManager commandManager,
            CommandOwnershipRegistry ownershipRegistry,
            Logger logger,
            String featureName
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.featureName = requireText(featureName, "featureName");
    }

    public synchronized void bindToFeature(String featureName) {
        requireOpen();
        if (!commands.isEmpty()) throw new IllegalStateException("Command manager cannot be rebound after registration");
        this.featureName = requireText(featureName, "featureName");
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public synchronized FeatureResourceState state() { return state; }

    public synchronized void registerBrigadierCommand(BrigadierCommand command) {
        requireOpen();
        Objects.requireNonNull(command, "command");
        String name = requireText(command.name(), "command name");
        if (commands.containsKey(name)) {
            throw new CommandRegistrationException("Feature '" + featureName
                    + "' attempted to register Brigadier command '" + name + "' twice");
        }
        List<String> aliases = sanitizeAliases(command.aliases(), name);
        CommandOwnershipRegistry.Registration claim = ownershipRegistry.claim(featureName, name, aliases);
        try {
            LiteralCommandNode<CommandSource> node = command.buildTree();
            var velocityCommand = new com.velocitypowered.api.command.BrigadierCommand(node);
            CommandMeta meta = commandManager.metaBuilder(velocityCommand)
                    .aliases(aliases.toArray(String[]::new)).plugin(plugin).build();
            commandManager.register(meta, velocityCommand);
            commands.put(name, command);
            metas.put(name, meta);
            ownership.put(name, claim);
            logger.info("[Brigadier] Registered /{} ({} aliases)", name, aliases.size());
        } catch (Throwable failure) {
            claim.close();
            throw new CommandRegistrationException(
                    "Failed to register required Brigadier command '" + name + "' for feature '" + featureName + "'",
                    failure
            );
        }
    }

    public synchronized void unregisterBrigadierCommand(String commandName) {
        BrigadierCommand command = commands.get(commandName);
        if (command == null) return;
        CommandMeta meta = metas.get(commandName);
        try {
            if (meta != null) commandManager.unregister(meta);
            else {
                commandManager.unregister(commandName);
                for (String alias : sanitizeAliases(command.aliases(), commandName)) commandManager.unregister(alias);
            }
        } catch (Throwable failure) {
            throw new CommandRegistrationException("Failed to unregister Brigadier command '" + commandName + "'", failure);
        }
        commands.remove(commandName);
        metas.remove(commandName);
        CommandOwnershipRegistry.Registration claim = ownership.remove(commandName);
        if (claim != null) claim.close();
        logger.info("[Brigadier] Unregistered /{}", commandName);
    }

    public synchronized void unregisterAllBrigadierCommands() {
        quiesce();
        Throwable failure = null;
        for (String name : new ArrayList<>(commands.keySet())) {
            try {
                unregisterBrigadierCommand(name);
            } catch (Throwable stepFailure) {
                if (failure == null) failure = stepFailure;
                else failure.addSuppressed(stepFailure);
            }
        }
        if (failure == null && commands.isEmpty()) state = FeatureResourceState.CLOSED;
        if (failure != null) throwUnchecked(failure);
    }

    public synchronized Map<String, BrigadierCommand> getRegisteredBrigadierCommands() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(commands));
    }

    public synchronized int getRegisteredBrigadierCommandCount() { return commands.size(); }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) throw new IllegalStateException("Command manager is " + state);
    }

    private static List<String> sanitizeAliases(Collection<String> aliases, String commandName) {
        if (aliases == null || aliases.isEmpty()) return List.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String alias : aliases) {
            if (alias == null) continue;
            String clean = alias.trim();
            if (!clean.isEmpty() && !clean.equalsIgnoreCase(commandName)) {
                result.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
            }
        }
        return List.copyOf(result.values());
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return clean;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }
}
