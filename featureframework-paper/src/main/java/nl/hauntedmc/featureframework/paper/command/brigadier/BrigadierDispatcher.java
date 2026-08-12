package nl.hauntedmc.featureframework.paper.command.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class BrigadierDispatcher {

    private final Plugin plugin;
    private final FrameworkLogger logger;
    private volatile @Nullable CommandDispatcher<CommandSourceStack> dispatcher;

    private static Field F_CHILDREN;
    private static Field F_LITERALS;
    private static Field F_ARGUMENTS;

    private final ReentrantLock writeLock = new ReentrantLock();

    static {
        try {
            F_CHILDREN = CommandNode.class.getDeclaredField("children");
            F_LITERALS = CommandNode.class.getDeclaredField("literals");
            F_ARGUMENTS = CommandNode.class.getDeclaredField("arguments");
            F_CHILDREN.setAccessible(true);
            F_LITERALS.setAccessible(true);
            F_ARGUMENTS.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    public BrigadierDispatcher(Plugin plugin, FrameworkLogger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    protected BrigadierDispatcher(
            Plugin plugin,
            FrameworkLogger logger,
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /**
     * Resolve Brigadier dispatcher via CraftServer → MinecraftServer → Commands → dispatcher. Safe to call many times.
     */
    public void resolveDispatcher() {
        if (this.dispatcher != null) return;
        try {
            Object disp = getBrigadierDispatcher();

            if (disp instanceof CommandDispatcher<?> cd) {
                @SuppressWarnings("unchecked")
                CommandDispatcher<CommandSourceStack> cast = (CommandDispatcher<CommandSourceStack>) cd;
                this.dispatcher = cast;
            }
        } catch (Throwable t) {
            logger.warn("[Brigadier] Failed to resolve dispatcher: " + t.getMessage());
        }
    }

    private Object getBrigadierDispatcher()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object craftServer = plugin.getServer(); // CraftServer
        Method mGetServer = craftServer.getClass().getMethod("getServer"); // -> MinecraftServer
        Object mcServer = mGetServer.invoke(craftServer);
        Method mGetCmds = mcServer.getClass().getMethod("getCommands"); // -> command holder
        Object cmds = mGetCmds.invoke(mcServer);
        Method mGetDisp = cmds.getClass().getMethod("getDispatcher");
        return mGetDisp.invoke(cmds);
    }

    public boolean attachBrigadierCommand(BrigadierCommand cmd) {
        return attachBrigadierCommand(cmd, cmd.name(), cmd.aliases());
    }

    public boolean attachBrigadierCommand(
            BrigadierCommand cmd,
            String primaryLabel,
            Collection<String> aliases
    ) {
        Objects.requireNonNull(cmd, "cmd");
        Objects.requireNonNull(primaryLabel, "primaryLabel");
        resolveDispatcher();
        final CommandDispatcher<CommandSourceStack> disp = this.dispatcher;
        if (disp == null) {
            logger.warn("[Brigadier] Dispatcher not available; cannot attach /" + primaryLabel);
            return false;
        }

        LinkedHashSet<String> aliasLabels = new LinkedHashSet<>();
        if (aliases != null) {
            aliases.stream()
                    .filter(Objects::nonNull)
                    .filter(alias -> !alias.equals(primaryLabel))
                    .forEach(aliasLabels::add);
        }

        writeLock.lock();
        try {
            RootCommandNode<CommandSourceStack> root = disp.getRoot();

            if (root.getChild(primaryLabel) != null) {
                return false;
            }
            for (String alias : aliasLabels) {
                if (root.getChild(alias) != null) {
                    return false;
                }
            }

            var node = cmd.buildTree();
            if (!primaryLabel.equals(node.getName())) {
                logger.warn("[Brigadier] Built root literal '" + node.getName()
                        + "' does not match registered label '" + primaryLabel + "'.");
                return false;
            }

            List<String> addedLabels = new ArrayList<>();
            try {
                root.addChild(node);
                addedLabels.add(primaryLabel);
                for (String alias : aliasLabels) {
                    root.addChild(Commands.literal(alias).redirect(node).build());
                    addedLabels.add(alias);
                }
            } catch (Throwable throwable) {
                addedLabels.forEach(label -> removeRootLiteral(disp, label));
                throw throwable;
            }
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean hasRootLiteral(String literal) {
        return getRootLiteral(literal) != null;
    }

    public @Nullable CommandNode<CommandSourceStack> getRootLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return null;
        }
        resolveDispatcher();
        CommandDispatcher<CommandSourceStack> current = dispatcher;
        return current == null ? null : current.getRoot().getChild(literal);
    }

    /**
     * Removes and returns one root node so it can be restored later.
     *
     * @throws IllegalStateException when the node exists but the dispatcher cannot be mutated safely
     */
    public @Nullable CommandNode<CommandSourceStack> takeRootLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return null;
        }
        resolveDispatcher();
        CommandDispatcher<CommandSourceStack> current = dispatcher;
        if (current == null) {
            return null;
        }

        writeLock.lock();
        try {
            CommandNode<CommandSourceStack> existing = current.getRoot().getChild(literal);
            if (existing == null) {
                return null;
            }
            if (!removeRootLiteral(current, literal)) {
                throw new IllegalStateException("Unable to remove Brigadier root '/" + literal + "'.");
            }
            return existing;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Restores a previously removed root node when the label is still free.
     */
    public boolean restoreRootLiteral(String literal, CommandNode<CommandSourceStack> node) {
        Objects.requireNonNull(literal, "literal");
        Objects.requireNonNull(node, "node");
        if (!literal.equals(node.getName())) {
            throw new IllegalArgumentException(
                    "Brigadier node name '" + node.getName() + "' does not match label '" + literal + "'."
            );
        }

        resolveDispatcher();
        CommandDispatcher<CommandSourceStack> current = dispatcher;
        if (current == null) {
            return false;
        }

        writeLock.lock();
        try {
            RootCommandNode<CommandSourceStack> root = current.getRoot();
            if (root.getChild(literal) != null) {
                return false;
            }
            root.addChild(node);
            return root.getChild(literal) != null;
        } finally {
            writeLock.unlock();
        }
    }

    public void detachBrigadierCommand(BrigadierCommand cmd) {
        List<String> labels = new ArrayList<>();
        labels.add(cmd.name());
        labels.addAll(cmd.aliases());
        detachBrigadierCommand(cmd, labels);
    }

    public void detachBrigadierCommand(BrigadierCommand cmd, Collection<String> registeredLabels) {
        resolveDispatcher();
        final CommandDispatcher<CommandSourceStack> disp = this.dispatcher;
        if (disp == null) {
            logger.warn("[Brigadier] Dispatcher not available; cannot detach /" + cmd.name());
            return;
        }

        writeLock.lock();
        try {
            boolean changed = false;
            for (String label : registeredLabels) {
                changed |= removeRootLiteral(disp, label);
            }
            if (!changed) {
                logger.info("[Brigadier] No dispatcher changes for /" + cmd.name()
                        + " (already absent?)");
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes a root literal by name from the dispatcher’s root node. Returns true if anything changed.
     */
    @SuppressWarnings("unchecked")
    public static boolean removeRootLiteral(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        if (dispatcher == null || literal == null || literal.isEmpty() || F_CHILDREN == null) return false;
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                    (Map<String, CommandNode<CommandSourceStack>>) F_CHILDREN.get(root);
            Map<String, CommandNode<CommandSourceStack>> literals =
                    (Map<String, CommandNode<CommandSourceStack>>) F_LITERALS.get(root);
            Map<String, CommandNode<CommandSourceStack>> arguments =
                    (Map<String, CommandNode<CommandSourceStack>>) F_ARGUMENTS.get(root);

            boolean changed = false;
            changed |= (children.remove(literal) != null);
            changed |= (literals.remove(literal) != null);
            changed |= (arguments.remove(literal) != null); // usually empty for root literals, but be thorough
            return changed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public @Nullable CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
