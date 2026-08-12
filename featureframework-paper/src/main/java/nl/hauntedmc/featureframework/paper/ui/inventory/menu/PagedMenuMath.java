package nl.hauntedmc.featureframework.paper.ui.inventory.menu;

final class PagedMenuMath {

    private PagedMenuMath() {
    }

    static int totalPages(int entryCount, int perPage) {
        if (perPage <= 0) return 1;
        return Math.max(1, (int) Math.ceil(entryCount / (double) perPage));
    }

    static int maxPageIndex(int entryCount, int perPage) {
        return totalPages(entryCount, perPage) - 1;
    }

    static int clampPageIndex(int requestedPage, int entryCount, int perPage) {
        if (requestedPage < 0) return 0;
        return Math.min(requestedPage, maxPageIndex(entryCount, perPage));
    }
}
