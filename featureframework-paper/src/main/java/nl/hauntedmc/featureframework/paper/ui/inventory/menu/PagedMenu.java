package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiClickContext;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiItem;
import nl.hauntedmc.featureframework.paper.ui.inventory.menu.item.GuiItemHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

/**
 * Multi-page menu with prev/next navigation and optional page info.
 * <p>
 * New API:
 * - You can customize the labels of the prev/next buttons via the Builder:
 * .prevLabel(Component.text("Vorige"))
 * .nextLabel(Component.text("Volgende"))
 * - When on the first page, the Prev button is hidden (slot gets the filler if present).
 * - When on the last page, the Next button is hidden (slot gets the filler if present).
 */
public final class PagedMenu<T> extends GuiMenu {

    private final List<T> entries;
    private final Function<T, GuiItem> renderer;
    private final List<Integer> contentSlots;
    private final int prevSlot;
    private final int nextSlot;
    private final Optional<Integer> pageInfoSlot;

    private final Component prevLabel;
    private final Component nextLabel;

    private final ItemStack fillerItem; // keep local copy to re-fill nav slots when hiding buttons

    private int pageIndex = 0;
    private final Map<Integer, GuiItem> dynamicItems = new HashMap<>();

    private PagedMenu(
            MenuNavigator gui,
            Component baseTitle,
            int size,
            boolean showPageInTitle,
            ItemStack filler,
            Map<Integer, GuiItem> items,
            boolean addBackButton,
            int backSlot,
            List<T> entries,
            Function<T, GuiItem> renderer,
            List<Integer> contentSlots,
            int prevSlot,
            int nextSlot,
            Optional<Integer> pageInfoSlot,
            Component prevLabel,
            Component nextLabel
    ) {
        super(gui, baseTitle, size, showPageInTitle, filler, items, addBackButton, backSlot);
        this.entries = new ArrayList<>(entries);
        this.renderer = renderer;
        this.contentSlots = new ArrayList<>(contentSlots);
        this.prevSlot = prevSlot;
        this.nextSlot = nextSlot;
        this.pageInfoSlot = pageInfoSlot;
        this.prevLabel = prevLabel == null ? Component.text("Previous") : prevLabel;
        this.nextLabel = nextLabel == null ? Component.text("Next") : nextLabel;
        this.fillerItem = filler == null ? null : filler.clone();
    }

    public static <T> Builder<T> builder(MenuNavigator gui) {
        return new Builder<>(gui);
    }

    @Override
    public Component titleFor(Player p) {
        if (!showPageInTitle) return baseTitle;
        int perPage = contentSlots.size();
        int total = PagedMenuMath.totalPages(entries.size(), perPage);
        return baseTitle.append(Component.text(" (" + (pageIndex + 1) + "/" + total + ")"));
    }

    @Override
    protected void afterPopulate(Player p, Inventory inv) {
        renderPage(p, inv);
    }

    private void renderPage(Player p, Inventory inv) {
        // Clear previous dynamic items in the content area
        for (int s : contentSlots) {
            inv.setItem(s, null);
            dynamicItems.remove(s);
        }

        int perPage = contentSlots.size();
        int start = pageIndex * perPage;

        // Render entries with permission-aware logic (fix)
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= entries.size()) break;

            GuiItem gi = renderer.apply(entries.get(idx));
            int slot = contentSlots.get(i);

            ItemStack toPlace;
            if (gi.visibleTo(p)) {
                toPlace = gi.renderFor(p);
            } else {
                toPlace = gi.replacementOrNull(p); // may be null (empty slot) or a barrier/etc
            }

            inv.setItem(slot, toPlace);
            dynamicItems.put(slot, gi);
        }

        // Compute page bounds
        int maxPage = PagedMenuMath.maxPageIndex(entries.size(), perPage);

        // Prev button: only when pageIndex > 0
        if (pageIndex > 0) {
            inv.setItem(prevSlot, GuiItemHelper.menuItem(Material.ARROW, prevLabel));
        } else {
            inv.setItem(prevSlot, fillerItem == null ? null : fillerItem.clone());
        }

        // Next button: only when pageIndex < maxPage
        if (pageIndex < maxPage) {
            inv.setItem(nextSlot, GuiItemHelper.menuItem(Material.ARROW, nextLabel));
        } else {
            inv.setItem(nextSlot, fillerItem == null ? null : fillerItem.clone());
        }

        // Optional page info item
        pageInfoSlot.ifPresent(s -> {
            int totalPages = Math.max(1, maxPage + 1);
            ItemStack info = GuiItemHelper.info(Component.text("Page " + (pageIndex + 1) + "/" + totalPages));
            inv.setItem(s, info);
        });
    }

    @Override
    public void handleClick(Player p, int slot, InventoryClickEvent e) {
        if (slot == prevSlot) {
            if (pageIndex > 0) {
                pageIndex--;
                gui.reopenSame(p, this);
            }
            return;
        }
        if (slot == nextSlot) {
            int perPage = contentSlots.size();
            int maxPage = PagedMenuMath.maxPageIndex(entries.size(), perPage);
            if (pageIndex < maxPage) {
                pageIndex++;
                gui.reopenSame(p, this);
            }
            return;
        }
        GuiItem gi = dynamicItems.get(slot);
        if (gi != null && gi.visibleTo(p)) {
            gi.click(p, new GuiClickContext(this, slot, e));
            return;
        }
        super.handleClick(p, slot, e);
    }

    public int pageIndex() {
        return pageIndex;
    }

    /**
     * Clamp and set the current page index.
     */
    public void setPageIndex(int page) {
        int perPage = contentSlots.size();
        this.pageIndex = PagedMenuMath.clampPageIndex(page, entries.size(), perPage);
    }

    public static final class Builder<T> {
        private final MenuNavigator gui;
        private Component title = Component.text("Menu");
        private int size = 9 * 6;
        private boolean pageInTitle = true;
        private ItemStack filler = null;
        private final Map<Integer, GuiItem> items = new HashMap<>();
        private boolean backButton = true;
        private int backSlot = -1;

        private List<T> entries = List.of();
        private Function<T, GuiItem> renderer = t -> GuiItemHelper.empty();
        private List<Integer> contentSlots = defaultGrid();
        private int prevSlot = 45; // bottom-left
        private int nextSlot = 53; // bottom-right
        private Optional<Integer> pageInfoSlot = Optional.of(49);

        private Component prevLabel = Component.text("Previous");
        private Component nextLabel = Component.text("Next");

        private Builder(MenuNavigator gui) {
            this.gui = Objects.requireNonNull(gui, "gui");
        }

        public Builder<T> title(Component t) {
            this.title = t;
            return this;
        }

        public Builder<T> size(int s) {
            this.size = s;
            return this;
        }

        public Builder<T> showPageInTitle(boolean b) {
            this.pageInTitle = b;
            return this;
        }

        public Builder<T> filler(ItemStack i) {
            this.filler = i;
            return this;
        }

        public Builder<T> item(int slot, GuiItem item) {
            this.items.put(slot, item);
            return this;
        }

        public Builder<T> backButton(boolean enabled) {
            this.backButton = enabled;
            return this;
        }

        public Builder<T> backButtonSlot(int slot) {
            this.backSlot = slot;
            return this;
        }

        public Builder<T> entries(List<T> es) {
            this.entries = es;
            return this;
        }

        public Builder<T> renderer(Function<T, GuiItem> r) {
            this.renderer = r;
            return this;
        }

        public Builder<T> contentSlots(List<Integer> s) {
            this.contentSlots = s;
            return this;
        }

        public Builder<T> prevSlot(int s) {
            this.prevSlot = s;
            return this;
        }

        public Builder<T> nextSlot(int s) {
            this.nextSlot = s;
            return this;
        }

        public Builder<T> pageInfoSlot(Optional<Integer> s) {
            this.pageInfoSlot = s;
            return this;
        }

        /**
         * Set the label (display name) for the "previous page" button.
         */
        public Builder<T> prevLabel(Component label) {
            this.prevLabel = label;
            return this;
        }

        /**
         * Set the label (display name) for the "next page" button.
         */
        public Builder<T> nextLabel(Component label) {
            this.nextLabel = label;
            return this;
        }

        /**
         * Convenience setter for both labels at once.
         */
        public Builder<T> navLabels(Component prev, Component next) {
            this.prevLabel = prev;
            this.nextLabel = next;
            return this;
        }

        public PagedMenu<T> build() {
            if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid size");
            if (contentSlots == null || contentSlots.isEmpty())
                throw new IllegalArgumentException("contentSlots cannot be empty");
            if (prevSlot < 0 || prevSlot >= size || nextSlot < 0 || nextSlot >= size) {
                throw new IllegalArgumentException("Prev/Next slots must be within inventory bounds");
            }
            pageInfoSlot.ifPresent(s -> {
                if (s < 0 || s >= size) throw new IllegalArgumentException("pageInfoSlot out of bounds");
            });
            validateFixedItems(items, size, backButton, backSlot, "PagedMenu");

            Set<Integer> reserved = new HashSet<>(contentSlots);
            ensureSlotsInBounds(contentSlots, size, "contentSlots");
            if (reserved.contains(prevSlot) || reserved.contains(nextSlot) || pageInfoSlot.map(reserved::contains).orElse(false))
                throw new IllegalArgumentException("contentSlots cannot include prev/next/pageInfo slots");
            if (backButton && backSlot >= 0 && (reserved.contains(backSlot)))
                throw new IllegalArgumentException("contentSlots cannot include back slot");

            for (int s : items.keySet()) {
                if (s == prevSlot || s == nextSlot || pageInfoSlot.orElse(-1) == s)
                    throw new IllegalArgumentException("Fixed items cannot override prev/next/pageInfo slots");
                if (backButton && backSlot >= 0 && s == backSlot)
                    throw new IllegalArgumentException("Fixed items cannot override back button slot");
                if (contentSlots.contains(s))
                    throw new IllegalArgumentException("Fixed items cannot be placed in contentSlots");
            }

            return new PagedMenu<>(gui, title, size, pageInTitle, filler, items, backButton, backSlot,
                    entries, renderer, contentSlots, prevSlot, nextSlot, pageInfoSlot, prevLabel, nextLabel);
        }

        private static List<Integer> defaultGrid() {
            // For a 6-row menu, use rows 2..5 (indexes 9..44); last row reserved for nav
            List<Integer> s = new ArrayList<>();
            for (int row = 1; row <= 4; row++) {
                for (int col = 0; col < 9; col++) s.add(row * 9 + col);
            }
            return s;
        }
    }
}
