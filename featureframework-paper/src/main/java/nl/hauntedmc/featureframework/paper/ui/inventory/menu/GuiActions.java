package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiClickContext;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Convenience actions for GuiItem click handlers.
 */
public final class GuiActions {
    private GuiActions() {
    }

    /**
     * Executes a command as the clicking player. Accepts commands with or without a leading slash.
     */
    public static Consumer<GuiClickContext> runPlayerCommand(String commandTemplate) {
        return ctx -> {
            String cmd = renderCommand(commandTemplate, ctx.player().getName());
            ctx.player().performCommand(normalizeLeadingSlash(cmd));
        };
    }

    /**
     * Executes a command from the console. Accepts commands with or without a leading slash.
     */
    public static Consumer<GuiClickContext> runConsoleCommand(String commandTemplate) {
        return ctx -> {
            String cmd = renderCommand(commandTemplate, ctx.player().getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalizeLeadingSlash(cmd));
        };
    }

    /**
     * Sends an Adventure Component message to the player.
     */
    public static Consumer<GuiClickContext> sendMessage(net.kyori.adventure.text.Component message) {
        return ctx -> ctx.player().sendMessage(message);
    }

    /**
     * Gives an item to the player. If inventory is full, leftover items are dropped at the player's location.
     * Supplier may create a fresh ItemStack per click.
     */
    public static Consumer<GuiClickContext> giveItem(Supplier<ItemStack> supplier) {
        return ctx -> {
            ItemStack is = supplier.get();
            if (is == null) return;
            var leftover = ctx.player().getInventory().addItem(is);
            leftover.values().forEach(drop -> ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), drop));
        };
    }

    /**
     * Opens a child menu resolved from the supplier.
     */
    public static Consumer<GuiClickContext> openChild(Supplier<GuiMenu> supplier) {
        return ctx -> ctx.openChild(supplier.get());
    }

    /**
     * Opens a root menu resolved from the supplier.
     */
    public static Consumer<GuiClickContext> openRoot(Supplier<GuiMenu> supplier) {
        return ctx -> ctx.openRoot(supplier.get());
    }

    /**
     * Plays a sound at the player's location.
     */
    public static Consumer<GuiClickContext> playSound(Sound sound, float volume, float pitch) {
        return ctx -> ctx.player().playSound(ctx.player().getLocation(), sound, volume, pitch);
    }

    static String renderCommand(String commandTemplate, String playerName) {
        return commandTemplate.replace("%player%", playerName);
    }

    static String normalizeLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
