package nl.hauntedmc.featureframework.integration.dataregistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerReferenceConverterTest {

    private final PlayerReferenceConverter converter = new PlayerReferenceConverter();

    @Test
    void roundTripPreservesCanonicalIdentityButNotTransientSnapshots() {
        PlayerReference reference = new PlayerReference(42L, "7d73c2d0-a9a7-4acb-a4e6-cc9ad0ef19c3", "Remy");

        assertEquals(42L, converter.convertToDatabaseColumn(reference));

        PlayerReference restored = converter.convertToEntityAttribute(42L);
        assertEquals(reference, restored);
        assertNull(restored.uuid());
        assertNull(restored.username());
    }

    @Test
    void convertsNullInBothDirections() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void rejectsInvalidPersistedIds() {
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(0L));
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(-1L));
    }
}
