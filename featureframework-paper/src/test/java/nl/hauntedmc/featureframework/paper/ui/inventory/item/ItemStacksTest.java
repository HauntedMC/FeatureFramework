package nl.hauntedmc.featureframework.paper.ui.inventory.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemStacksTest {

    @Test
    void prettyMaterialConvertsEnumNameToReadableText() {
        assertEquals("oak sign", ItemStacks.prettyMaterial(Material.OAK_WALL_SIGN));
        assertEquals("diamond sword", ItemStacks.prettyMaterial(Material.DIAMOND_SWORD));
    }

    @Test
    void bestDisplayNameReturnsAirWhenStackMissing() {
        assertEquals(Component.text("Air"), ItemStacks.bestDisplayName(null));
    }
}
