package nl.hauntedmc.featureframework.toolkit.collection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypedListsTest {

    @Test
    void createsAnImmutableTypedCopy() {
        List<String> result = TypedLists.copyOf(List.of("first", "second"), String.class);

        assertEquals(List.of("first", "second"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("third"));
    }

    @Test
    void nonListValuesBecomeEmptyLists() {
        assertEquals(List.of(), TypedLists.copyOf("not a list", String.class));
        assertEquals(List.of(), TypedLists.copyOf(null, String.class));
    }

    @Test
    void rejectsElementsWithTheWrongType() {
        List<Object> values = List.of("valid", 42);

        assertThrows(ClassCastException.class, () -> TypedLists.copyOf(values, String.class));
    }
}
