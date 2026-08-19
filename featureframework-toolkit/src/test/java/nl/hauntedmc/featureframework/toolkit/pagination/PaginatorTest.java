package nl.hauntedmc.featureframework.toolkit.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatorTest {
    @Test
    void emptyInputAndPageClampingAreStable() {
        Paginator.Page<String> empty = Paginator.paginate(null, 4, 10);
        assertEquals(List.of(), empty.items());
        assertEquals(1, empty.page());
        assertEquals(1, empty.totalPages());
        assertTrue(empty.isFirst());
        assertTrue(empty.isLast());
        assertFalse(empty.hasPrevious());
        assertFalse(empty.hasNext());
        assertEquals(1, empty.previousPage());
        assertEquals(1, empty.nextPage());

        Paginator.Page<Integer> middle = Paginator.paginate(List.of(1, 2, 3, 4, 5), 2, 2);
        assertTrue(middle.hasPrevious());
        assertTrue(middle.hasNext());
        assertFalse(middle.isFirst());
        assertFalse(middle.isLast());
        assertEquals(1, middle.previousPage());
        assertEquals(3, middle.nextPage());

        Paginator.Page<Integer> last = Paginator.paginate(List.of(1, 2, 3, 4, 5), 99, 2);
        assertEquals(List.of(5), last.items());
        assertEquals(3, last.page());
        assertEquals(3, last.totalPages());
        assertEquals(5, last.totalItems());
        assertTrue(last.isLast());
        assertFalse(last.hasNext());
        assertEquals(3, last.nextPage());
        assertThrows(IllegalArgumentException.class, () -> Paginator.paginate(List.of(1), 1, 0));
    }
}
