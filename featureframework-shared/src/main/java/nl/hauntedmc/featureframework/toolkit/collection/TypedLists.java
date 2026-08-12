package nl.hauntedmc.featureframework.toolkit.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Type-safe conversion helpers for untyped configuration values. */
public final class TypedLists {

    private TypedLists() {
    }

    /**
     * Returns an immutable, type-checked copy of {@code value}, or an empty list when the value is not a list.
     *
     * @throws ClassCastException when a list element does not have the requested type
     */
    public static <T> List<T> copyOf(Object value, Class<T> elementType) {
        Objects.requireNonNull(elementType, "elementType");
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        List<T> result = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!elementType.isInstance(element)) {
                String actualType = element == null ? "null" : element.getClass().getName();
                throw new ClassCastException(
                        "Expected " + elementType.getName() + " list element, but found " + actualType
                );
            }
            result.add(elementType.cast(element));
        }
        return List.copyOf(result);
    }
}
