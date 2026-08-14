package nl.hauntedmc.featureframework.data.audit;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerReference;
import nl.hauntedmc.featureframework.integration.dataregistry.PlayerReferenceResolver;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.hibernate.Session;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Reusable ORM transaction and player-reference support for feature-owned audit services. */
public abstract class AbstractPlayerAuditLogService {
    private final FrameworkLogger logger;
    private final ORMContext orm;
    private final PlayerReferenceResolver playerResolver;

    protected AbstractPlayerAuditLogService(
            FrameworkLogger logger,
            ORMContext orm,
            PlayerReferenceResolver playerResolver
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.orm = orm;
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    protected final void persist(String action, Consumer<Session> writer) {
        if (orm == null) return;
        try {
            orm.runInTransaction(session -> {
                writer.accept(session);
                return null;
            });
        } catch (Exception failure) {
            logger.warn("Failed to persist " + action, failure);
        }
    }

    protected final PlayerReference resolvePlayer(Session session, UUID playerId) {
        Objects.requireNonNull(session, "session");
        return playerResolver.resolveReference(playerId);
    }

    protected final String normalize(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
