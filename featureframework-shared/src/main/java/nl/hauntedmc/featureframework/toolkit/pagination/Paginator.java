package nl.hauntedmc.featureframework.toolkit.pagination;

import java.util.Collections;
import java.util.List;

/** Generic 1-based list pagination. */
public final class Paginator {
    private Paginator() { }

    public record Page<T>(List<T> items, int page, int totalPages, int totalItems, int pageSize) { }

    public static <T> Page<T> paginate(List<T> all, int page, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be positive");
        if (all == null || all.isEmpty()) return new Page<>(Collections.emptyList(), 1, 1, 0, pageSize);
        int total = all.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int current = Math.min(Math.max(1, page), pages);
        int from = (current - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        return new Page<>(List.copyOf(all.subList(from, to)), current, pages, total, pageSize);
    }
}
