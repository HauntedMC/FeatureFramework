package nl.hauntedmc.featureframework.integration.dataregistry;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a feature-local player reference as its scalar DataRegistry player id.
 *
 * <p>This converter intentionally does not restore UUID or username snapshots. They are not durable
 * feature data; callers that require current player details must resolve the id through DataRegistry.</p>
 */
@Converter(autoApply = false)
public final class PlayerReferenceConverter implements AttributeConverter<PlayerReference, Long> {
    @Override
    public Long convertToDatabaseColumn(PlayerReference reference) {
        return reference == null ? null : reference.id();
    }

    @Override
    public PlayerReference convertToEntityAttribute(Long playerId) {
        return playerId == null ? null : PlayerReference.byId(playerId);
    }
}
