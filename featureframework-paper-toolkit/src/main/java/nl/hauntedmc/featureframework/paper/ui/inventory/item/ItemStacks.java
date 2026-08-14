package nl.hauntedmc.featureframework.paper.ui.inventory.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * Utilities for working with ItemStacks in a UI-friendly way.
 */
public final class ItemStacks {

    private ItemStacks() {
    }

    /**
     * Get the best-available display name for an ItemStack:
     * 1) Custom display name (component) if present
     * 2) Client-translated name via translation key (Paper)
     * 3) Pretty-printed enum name
     */
    public static Component bestDisplayName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Component.text("Air");
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName();
        }
        // Try Paper translation key for a client-localized name
        try {
            String key = stack.getType().translationKey();
            return Component.translatable(key);
        } catch (Throwable ignored) {
            // Fall back below
        }
        return Component.text(prettyMaterial(stack.getType()));
    }

    /**
     * Pretty print a Material enum to human-friendly text.
     */
    public static String prettyMaterial(Material mat) {
        String base = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        // Quick cleanups, extend as needed
        base = base.replace("wall sign", "sign");
        return base;
    }
}
