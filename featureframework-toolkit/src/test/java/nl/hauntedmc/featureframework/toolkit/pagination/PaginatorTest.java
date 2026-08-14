package nl.hauntedmc.featureframework.toolkit.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginatorTest {
    @Test
    void emptyInputAndPageClampingAreStable() {
        Paginator.Page<String> empty = Paginator.paginate(null, 4, 10);
        assertEquals(List.of(), empty.items());
        assertEquals(1, empty.page());
        assertEquals(1, empty.totalPages());
        Paginator.Page<Integer> last = Paginator.paginate(List.of(1, 2, 3, 4, 5), 99, 2);
        assertEquals(List.of(5), last.items());
        assertEquals(3, last.page());
        assertEquals(3, last.totalPages());
        assertEquals(5, last.totalItems());
        assertThrows(IllegalArgumentException.class, () -> Paginator.paginate(List.of(1), 1, 0));
    }
}
