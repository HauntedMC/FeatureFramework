package nl.hauntedmc.featureframework.paper.command;

import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;

import java.util.*;

/**
 * Temporarily claims unnamespaced command labels from Bukkit and Brigadier.
 * Namespaced fallbacks are intentionally left untouched.
 */
public final class CommandRegistryTakeover {

    private final CommandMap commandMap;
    private final BrigadierDispatcher brigadierDispatcher;

    public CommandRegistryTakeover(CommandMap commandMap, BrigadierDispatcher brigadierDispatcher) {
        this.commandMap = Objects.requireNonNull(commandMap, "commandMap");
        this.brigadierDispatcher = Objects.requireNonNull(brigadierDispatcher, "brigadierDispatcher");
    }

    public Claim claim(Collection<String> labels, boolean overwriteConflicts) {
        Map<String, Command> bukkitConflicts = new LinkedHashMap<>();
        Map<String, CommandNode<CommandSourceStack>> brigadierConflicts = new LinkedHashMap<>();
        Map<String, Command> knownCommands = commandMap.getKnownCommands();

        for (String label : labels) {
            Command bukkitCommand = knownCommands.get(label);
            CommandNode<CommandSourceStack> brigadierRoot = brigadierDispatcher.getRootLiteral(label);
            if (bukkitCommand != null) {
                bukkitConflicts.put(label, bukkitCommand);
            }
            if (brigadierRoot != null) {
                brigadierConflicts.put(label, brigadierRoot);
            }
        }

        if (bukkitConflicts.isEmpty() && brigadierConflicts.isEmpty()) {
            return Claim.claimed(Takeover.empty(), List.of());
        }

        List<Conflict> conflicts = labels.stream()
                .filter(label -> bukkitConflicts.containsKey(label) || brigadierConflicts.containsKey(label))
                .map(label -> new Conflict(
                        label,
                        bukkitConflicts.get(label),
                        brigadierConflicts.containsKey(label)
                ))
                .toList();
        if (!overwriteConflicts) {
            return Claim.blocked(conflicts.get(0), conflicts);
        }

        Map<String, Command> removedBukkit = new LinkedHashMap<>();
        Map<String, CommandNode<CommandSourceStack>> removedBrigadier = new LinkedHashMap<>();
        try {
            /*
             * Paper links the legacy command map and Brigadier dispatcher. Removing one side can therefore
             * remove the matching entry from the other side as a side effect. Remove Brigadier first, then
             * accept an already-absent Bukkit binding when it still matches the command we snapshotted.
             */
            for (Map.Entry<String, CommandNode<CommandSourceStack>> entry : brigadierConflicts.entrySet()) {
                CommandNode<CommandSourceStack> removed = brigadierDispatcher.takeRootLiteral(entry.getKey());
                if (removed == null) {
                    throw new IllegalStateException(
                            "Brigadier command root '/" + entry.getKey() + "' changed during takeover."
                    );
                }
                removedBrigadier.put(entry.getKey(), removed);
            }
            for (Map.Entry<String, Command> entry : bukkitConflicts.entrySet()) {
                removeBukkitBinding(knownCommands, entry.getKey(), entry.getValue());
                removedBukkit.put(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException | Error throwable) {
            Takeover partial = new Takeover(removedBukkit, removedBrigadier);
            try {
                restore(partial);
            } catch (RuntimeException | Error restoreFailure) {
                throwable.addSuppressed(restoreFailure);
            }
            throw throwable;
        }

        return Claim.claimed(new Takeover(removedBukkit, removedBrigadier), conflicts);
    }

    private static void removeBukkitBinding(
            Map<String, Command> knownCommands,
            String label,
            Command expected
    ) {
        Command current = knownCommands.get(label);
        if (current == null) {
            // The paired Brigadier removal already removed this exact plain label.
            return;
        }
        if (current != expected) {
            throw new IllegalStateException(
                    "Bukkit command label '/" + label + "' changed during takeover."
            );
        }
        if (!knownCommands.remove(label, expected)) {
            current = knownCommands.get(label);
            if (current == null) {
                return;
            }
            throw new IllegalStateException(
                    "Bukkit command label '/" + label + "' changed during takeover."
            );
        }
    }

    public RestoreResult restore(Takeover takeover) {
        Objects.requireNonNull(takeover, "takeover");
        Set<String> skippedBukkit = new LinkedHashSet<>();
        Set<String> skippedBrigadier = new LinkedHashSet<>();
        Map<String, Command> knownCommands = commandMap.getKnownCommands();

        // Restore the Brigadier side first so a Bukkit restoration cannot make it appear as a conflicting new root.
        for (Map.Entry<String, CommandNode<CommandSourceStack>> entry : takeover.brigadierRoots().entrySet()) {
            if (!brigadierDispatcher.restoreRootLiteral(entry.getKey(), entry.getValue())) {
                skippedBrigadier.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Command> entry : takeover.bukkitCommands().entrySet()) {
            if (knownCommands.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                skippedBukkit.add(entry.getKey());
            }
        }
        return new RestoreResult(skippedBukkit, skippedBrigadier);
    }

    public record Conflict(String label, Command bukkitCommand, boolean brigadierRoot) {

        public String ownerDescription() {
            String bukkitOwner = null;
            if (bukkitCommand instanceof PluginIdentifiableCommand identifiable) {
                bukkitOwner = identifiable.getPlugin().getName();
            } else if (bukkitCommand != null) {
                bukkitOwner = bukkitCommand.getClass().getName();
            }

            if (bukkitOwner != null && brigadierRoot) {
                return bukkitOwner + " and the Brigadier dispatcher";
            }
            if (bukkitOwner != null) {
                return bukkitOwner;
            }
            return "the Brigadier dispatcher";
        }
    }

    public record Claim(boolean claimed, Conflict blockingConflict, Takeover takeover, List<Conflict> conflicts) {

        public static Claim claimed(Takeover takeover, List<Conflict> conflicts) {
            return new Claim(true, null, takeover, List.copyOf(conflicts));
        }

        public static Claim blocked(Conflict blockingConflict, List<Conflict> conflicts) {
            return new Claim(false, blockingConflict, Takeover.empty(), List.copyOf(conflicts));
        }
    }

    public record Takeover(
            Map<String, Command> bukkitCommands,
            Map<String, CommandNode<CommandSourceStack>> brigadierRoots
    ) {
        public Takeover {
            bukkitCommands = Map.copyOf(bukkitCommands);
            brigadierRoots = Map.copyOf(brigadierRoots);
        }

        public static Takeover empty() {
            return new Takeover(Map.of(), Map.of());
        }

        public boolean isEmpty() {
            return bukkitCommands.isEmpty() && brigadierRoots.isEmpty();
        }
    }

    public record RestoreResult(Set<String> skippedBukkitLabels, Set<String> skippedBrigadierLabels) {
        public RestoreResult {
            skippedBukkitLabels = Set.copyOf(skippedBukkitLabels);
            skippedBrigadierLabels = Set.copyOf(skippedBrigadierLabels);
        }

        public boolean complete() {
            return skippedBukkitLabels.isEmpty() && skippedBrigadierLabels.isEmpty();
        }
    }
}
