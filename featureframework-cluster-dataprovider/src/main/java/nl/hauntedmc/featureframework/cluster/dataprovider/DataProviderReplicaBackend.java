package nl.hauntedmc.featureframework.cluster.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.keyvalue.KeyValueDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;

import java.util.Objects;

/** Owns the isolated DataProvider registrations used by one FeatureFramework replica controller. */
public final class DataProviderReplicaBackend implements AutoCloseable {
    private final DataProviderScope scope;
    private final DataProviderReplicaGenerationRepository generations;
    private final DataProviderReplicaLeaseCoordinator leases;

    private DataProviderReplicaBackend(
            DataProviderScope scope,
            DataProviderReplicaGenerationRepository generations,
            DataProviderReplicaLeaseCoordinator leases
    ) {
        this.scope = scope;
        this.generations = generations;
        this.leases = leases;
    }

    /**
     * Opens the isolated MySQL/Redis backend and verifies that the explicit replica schema is installed.
     * The scope is closed again if registration or validation fails.
     */
    public static DataProviderReplicaBackend open(
            DataProviderAPI api,
            String mysqlConnection,
            String redisConnection
    ) {
        Objects.requireNonNull(api, "api");
        DataProviderScope scope = api.scope("featureframework.cluster");
        try {
            RelationalDatabaseProvider relational = scope.registerDatabaseOrThrow(
                    DatabaseType.MYSQL, text(mysqlConnection, "mysqlConnection"), RelationalDatabaseProvider.class);
            KeyValueDatabaseProvider redis = scope.registerDatabaseOrThrow(
                    DatabaseType.REDIS, text(redisConnection, "redisConnection"), KeyValueDatabaseProvider.class);
            DataProviderReplicaGenerationRepository generations =
                    new DataProviderReplicaGenerationRepository(relational.getDataAccess());
            generations.validateSchema().toCompletableFuture().join();
            return new DataProviderReplicaBackend(
                    scope,
                    generations,
                    new DataProviderReplicaLeaseCoordinator(redis.getCoordinationDataAccess())
            );
        } catch (RuntimeException failure) {
            scope.close();
            throw failure;
        }
    }

    public DataProviderReplicaGenerationRepository generations() { return generations; }
    public DataProviderReplicaLeaseCoordinator leases() { return leases; }
    public DataProviderScope scope() { return scope; }

    @Override public void close() { scope.close(); }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
