package nl.hauntedmc.featureframework.paper.ui.inventory.preview.inv;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;

/**
 * Immutable snapshot of a player's inventory at a point in time.
 * - main: 27 slots (the 3x9 "storage" rows)
 * - hotbar: 9 slots
 * - armor: helmet, chestplate, leggings, boots
 * - offhand
 */
public final class InventorySnapshot {
    private final ItemStack[] main;   // length 27
    private final ItemStack[] hotbar; // length 9
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final ItemStack offhand;
    private final String inventoryOwner;

    private InventorySnapshot(
            ItemStack[] main,
            ItemStack[] hotbar,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offhand,
            String inventoryOwner
    ) {
        this.main = main;
        this.hotbar = hotbar;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.offhand = offhand;
        this.inventoryOwner = inventoryOwner;
    }

    public static InventorySnapshot from(Player player) {
        PlayerInventory inv = player.getInventory();

        // PlayerInventory storage layout: hotbar [0..8], main/storage [9..35]
        ItemStack[] main = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack it = inv.getItem(9 + i);
            main[i] = cloneOrNull(it);
        }

        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            hotbar[i] = cloneOrNull(it);
        }

        return new InventorySnapshot(
                main,
                hotbar,
                cloneOrNull(inv.getHelmet()),
                cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()),
                cloneOrNull(inv.getBoots()),
                cloneOrNull(inv.getItemInOffHand()),
                player.getName()
        );
    }

    private static ItemStack cloneOrNull(ItemStack it) {
        return (it == null || it.getType().isAir()) ? null : it.clone();
    }

    public ItemStack[] main() {
        return Arrays.copyOf(main, main.length);
    }

    public ItemStack[] hotbar() {
        return Arrays.copyOf(hotbar, hotbar.length);
    }

    public ItemStack helmet() {
        return cloneOrNull(helmet);
    }

    public ItemStack chestplate() {
        return cloneOrNull(chestplate);
    }

    public ItemStack leggings() {
        return cloneOrNull(leggings);
    }

    public ItemStack boots() {
        return cloneOrNull(boots);
    }

    public ItemStack offhand() {
        return cloneOrNull(offhand);
    }

    public String inventoryOwner() {
        return inventoryOwner;
    }

}
