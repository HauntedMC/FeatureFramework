package nl.hauntedmc.featureframework.paper.ui.inventory.menu.item;

import nl.hauntedmc.featureframework.paper.ui.inventory.menu.ConfirmationMenu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Item abstraction used by GUIs.
 */
public final class GuiItem {
    private final Function<Player, ItemStack> factory;
    private final Predicate<Player> visibility;
    private final String permission;
    private final Consumer<GuiClickContext> onClick;
    private final Function<Player, ItemStack> replacementIfNoPerm;
    private final boolean requiresConfirmation;
    private final Function<Player, ConfirmationMenu> confirmationFactory;

    private final boolean closeOnClick;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private GuiItem(
            Function<Player, ItemStack> factory,
            Predicate<Player> visibility,
            String permission,
            Consumer<GuiClickContext> onClick,
            Function<Player, ItemStack> replacementIfNoPerm,
            boolean requiresConfirmation,
            Function<Player, ConfirmationMenu> confirmationFactory,
            boolean closeOnClick,
            long cooldownMillis
    ) {
        this.factory = factory;
        this.visibility = visibility;
        this.permission = permission;
        this.onClick = onClick;
        this.replacementIfNoPerm = replacementIfNoPerm;
        this.requiresConfirmation = requiresConfirmation;
        this.confirmationFactory = confirmationFactory;
        this.closeOnClick = closeOnClick;
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether this item is visible to the player, based on predicate and permission.
     */
    public boolean visibleTo(Player p) {
        boolean vis = visibility == null || visibility.test(p);
        boolean permOk = permission == null || permission.isBlank() || p.hasPermission(permission);
        return vis && permOk;
    }

    /**
     * Replacement to render when not visible. Returns null if no replacement configured.
     */
    public ItemStack replacementOrNull(Player p) {
        if (visibleTo(p)) return null;
        return replacementIfNoPerm != null ? replacementIfNoPerm.apply(p) : null;
    }

    /**
     * Render the ItemStack for this player.
     */
    public ItemStack renderFor(Player p) {
        return factory.apply(p);
    }

    /**
     * Handle a click on this item.
     */
    public void click(Player p, GuiClickContext ctx) {
        if (requiresConfirmation && confirmationFactory != null) {
            ConfirmationMenu confirm = confirmationFactory.apply(p);
            ctx.openChild(confirm);
            return;
        }
        if (cooldownMillis > 0) {
            long now = System.currentTimeMillis();
            Long last = lastClick.get(p.getUniqueId());
            if (last != null && (now - last) < cooldownMillis) {
                return;
            }
            lastClick.put(p.getUniqueId(), now);
        }
        try {
            if (onClick != null) onClick.accept(ctx);
        } catch (Throwable ignored) {
        }
        if (closeOnClick) {
            p.closeInventory();
        }
    }

    public static final class Builder {
        private Function<Player, ItemStack> factory = pl -> new ItemStack(org.bukkit.Material.BARRIER);
        private Predicate<Player> visibility = p -> true;
        private String permission = null;
        private Consumer<GuiClickContext> onClick = null;
        private Function<Player, ItemStack> replacementIfNoPerm = null;
        private boolean requiresConfirmation = false;
        private Function<Player, ConfirmationMenu> confirmationFactory = null;
        private boolean closeOnClick = false;
        private long cooldownMillis = 0;

        public Builder factory(Function<Player, ItemStack> f) {
            this.factory = f;
            return this;
        }

        public Builder visibleWhen(Predicate<Player> v) {
            this.visibility = v;
            return this;
        }

        public Builder permission(String perm) {
            this.permission = perm;
            return this;
        }

        public Builder onClick(Consumer<GuiClickContext> c) {
            this.onClick = c;
            return this;
        }

        public Builder replacementIfNoPerm(Function<Player, ItemStack> r) {
            this.replacementIfNoPerm = r;
            return this;
        }

        public Builder confirm(Function<Player, ConfirmationMenu> f) {
            this.requiresConfirmation = true;
            this.confirmationFactory = f;
            return this;
        }

        /**
         * Close the menu after the click action completes.
         */
        public Builder closeMenuOnClick(boolean b) {
            this.closeOnClick = b;
            return this;
        }

        /**
         * Debounce spam-clicking of this item, in milliseconds.
         */
        public Builder cooldownMillis(long ms) {
            this.cooldownMillis = Math.max(0, ms);
            return this;
        }

        public GuiItem build() {
            if (factory == null) throw new IllegalArgumentException("GuiItem.factory cannot be null");
            return new GuiItem(factory, visibility, permission, onClick, replacementIfNoPerm,
                    requiresConfirmation, confirmationFactory, closeOnClick, cooldownMillis);
        }
    }
}
