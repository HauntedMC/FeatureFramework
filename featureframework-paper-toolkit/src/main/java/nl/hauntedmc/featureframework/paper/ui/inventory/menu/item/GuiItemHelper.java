package nl.hauntedmc.featureframework.paper.ui.inventory.menu.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentWordWrap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for building consistent GUI ItemStacks.
 * Provides:
 * - Generic button(name, lore)
 * - Info paper(name, lore)
 * - Filler pane
 * - Common controls (back arrow, close barrier, locked/permission-gated)
 * - Empty GuiItem factory
 * Default styling: NO ITALICS on display name and lore unless you explicitly add italics yourself
 * after creating the ItemStack.
 */
public final class GuiItemHelper {
    private GuiItemHelper() {
    }

    /**
     * Ensure a component renders without italics.
     */
    private static Component noItalics(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Apply no-italics to a list of components.
     */
    private static List<Component> noItalics(List<Component> cs) {
        return cs.stream().map(GuiItemHelper::noItalics).collect(Collectors.toList());
    }

    public static ItemStack menuItemWrapped(Material type, Component name, int loreWidth, Component... lore) {
        ItemStack is = new ItemStack(type);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(noItalics(name));

        if (lore != null && lore.length > 0) {
            List<Component> wrapped = new java.util.ArrayList<>();
            for (Component c : lore) wrapped.addAll(ComponentWordWrap.wrap(c, loreWidth));
            meta.lore(wrapped);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
        is.setItemMeta(meta);
        return is;
    }

    /**
     * Generic button-style item with a display name and optional lore (non-italic by default).
     */
    public static ItemStack menuItem(Material type, Component name, Component... lore) {
        ItemStack is = new ItemStack(type);
        ItemMeta meta = is.getItemMeta();

        // Force non-italic by default for readability in GUIs
        meta.displayName(noItalics(name));
        if (lore != null && lore.length > 0) {
            meta.lore(noItalics(Arrays.asList(lore)));
        }

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DYE
        );
        is.setItemMeta(meta);
        return is;
    }

    /**
     * Paper info card with a name and optional lore (non-italic by default).
     */
    public static ItemStack info(Component name, Component... lore) {
        return menuItem(Material.CLOCK, name, lore);
    }

    /**
     * Neutral glass filler pane.
     */
    public static ItemStack filler() {
        return menuItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
    }

    /**
     * Standard back arrow item.
     */
    public static ItemStack backArrow() {
        return menuItem(Material.ARROW, Component.text("Back"));
    }

    /**
     * Empty gui item factory that renders AIR.
     */
    public static GuiItem empty() {
        return GuiItem.builder().factory(p -> new ItemStack(Material.AIR)).build();
    }
}
