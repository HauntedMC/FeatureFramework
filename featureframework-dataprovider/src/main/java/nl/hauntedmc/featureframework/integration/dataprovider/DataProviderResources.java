package nl.hauntedmc.featureframework.integration.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DataAccess;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LogLevel;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;
import nl.hauntedmc.featureframework.resource.ResourceKey;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Feature-scoped DataProvider resource owner shared by every platform.
 * Database scopes, ORM contexts, messaging handles, and cleanup are isolated per feature.
 */
public class DataProviderResources {
    public static final ResourceKey<DataProviderResources> KEY = ResourceKey.of(DataProviderResources.class);
    private static final String DEFAULT_PLAYER_ORM_IDENTIFIER = "playerOrmContext";
    private static final String DEFAULT_SYSTEM_ORM_IDENTIFIER = "systemOrmContext";

    private final Object hostOwner;
    private final Supplier<DataProviderAPI> apiSupplier;
    private final Supplier<String> schemaModeSupplier;
    private final FrameworkLogger logger;
    private final LoggerAdapter ormLogger;
    private final boolean fixedFacade;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, ORMContext> ormContexts = new ConcurrentHashMap<>();

    private volatile DataProviderAPI boundApi;
    private volatile DataProviderScope scope;
    private volatile FeatureResourceState state = FeatureResourceState.OPEN;
    private volatile String lastOrmIdentifier;
    private String featureName;
    private boolean initialized;

    public DataProviderResources(
            Object hostOwner,
            Supplier<DataProviderAPI> apiSupplier,
            FrameworkLogger logger,
            Supplier<String> schemaModeSupplier
    ) {
        this(hostOwner, apiSupplier, logger, schemaModeSupplier, false, null);
    }

    public DataProviderResources(
            Object hostOwner,
            DataProviderAPI fixedApi,
            FrameworkLogger logger,
            Supplier<String> schemaModeSupplier
    ) {
        this(hostOwner, () -> fixedApi, logger, schemaModeSupplier, true, fixedApi);
    }

    private DataProviderResources(
            Object hostOwner,
            Supplier<DataProviderAPI> apiSupplier,
            FrameworkLogger logger,
            Supplier<String> schemaModeSupplier,
            boolean fixedFacade,
            DataProviderAPI boundApi
    ) {
        this.hostOwner = Objects.requireNonNull(hostOwner, "hostOwner");
        this.apiSupplier = Objects.requireNonNull(apiSupplier, "apiSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.schemaModeSupplier = Objects.requireNonNull(schemaModeSupplier, "schemaModeSupplier");
        this.fixedFacade = fixedFacade;
        this.boundApi = boundApi;
        this.ormLogger = new FrameworkLoggerAdapter(logger);
    }

    public synchronized void bindToFeature(String featureName) {
        String normalized = normalize(featureName);
        if (normalized == null) {
            this.featureName = null;
            initialized = false;
            state = FeatureResourceState.OPEN;
            return;
        }
        if (hasActiveResources() && this.featureName != null && !this.featureName.equals(normalized)) {
            throw new IllegalStateException("Cannot rebind data resources from feature '" + this.featureName
                    + "' to '" + normalized + "' while resources are active");
        }
        if (normalized.equals(this.featureName) && initialized && scope != null && state == FeatureResourceState.OPEN) {
            return;
        }
        if (!hasActiveResources()) closeScope();
        this.featureName = normalized;
        initialized = false;
        state = FeatureResourceState.OPEN;
    }

    public void initializeForFeature(String featureName) {
        bindToFeature(featureName);
        initializeBoundFeature();
    }

    public void quiesce() {
        if (state == FeatureResourceState.OPEN) state = FeatureResourceState.QUIESCING;
    }

    public FeatureResourceState state() { return state; }
    public boolean isInitialized() { return initialized; }

    private boolean initializeBoundFeature() {
        requireOpen();
        if (featureName == null) {
            logger.error("Feature name is not bound to its DataProvider manager");
            return false;
        }
        Optional<DataProviderAPI> api = api();
        if (api.isEmpty()) {
            logger.error("DataProviderAPI is unavailable for feature '" + featureName + "'");
            return false;
        }
        try {
            scope = api.get().scope("feature." + featureName);
            initialized = true;
            logger.info("DataProvider scope initialized for feature '" + featureName + "'");
            return true;
        } catch (RuntimeException failure) {
            logger.error("Could not create DataProvider scope for feature '" + featureName + "'", failure);
            return false;
        }
    }

    public Optional<DatabaseProvider> registerDatabaseConnection(
            String identifier, DatabaseType type, String connectionName) {
        if (!hasText(identifier) || type == null || !hasText(connectionName)) {
            logger.error("Invalid database registration request for feature '" + featureName + "'");
            return Optional.empty();
        }
        if (!isReady()) return Optional.empty();
        Connection existing = connections.get(identifier);
        if (existing != null && existing.type == type && existing.connectionName.equals(connectionName)) {
            return Optional.of(existing.provider);
        }
        try {
            DataProviderScope current = scope;
            if (current == null) return Optional.empty();
            DatabaseProvider provider = current.registerDatabaseOrThrow(type, connectionName);
            if (provider == null) {
                unregister(type, connectionName, identifier);
                return Optional.empty();
            }
            Connection replacement = new Connection(type, connectionName, provider);
            Connection previous = connections.put(identifier, replacement);
            if (previous != null && previous != replacement) release(previous, identifier);
            logger.info("Registered connection '" + identifier + "' (" + type + ") for feature '" + featureName + "'");
            return Optional.of(provider);
        } catch (Exception failure) {
            logger.error("Failed to register connection '" + identifier + "' for feature '" + featureName + "'", failure);
            return Optional.empty();
        }
    }

    public Optional<DatabaseProvider> registerConnection(String identifier, DatabaseType type, String connectionName) {
        return registerDatabaseConnection(identifier, type, connectionName);
    }

    public Optional<DatabaseProvider> getDatabaseProvider(String identifier) {
        Connection connection = hasText(identifier) ? connections.get(identifier) : null;
        return connection == null ? Optional.empty() : Optional.of(connection.provider);
    }

    public Optional<DatabaseProvider> getDataProvider(String identifier) { return getDatabaseProvider(identifier); }

    public <T extends DataAccess> Optional<T> registerDataAccess(
            String identifier, DatabaseType type, String connectionName, Class<T> expectedType) {
        if (expectedType == null) return Optional.empty();
        return registerDatabaseConnection(identifier, type, connectionName)
                .flatMap(provider -> typedAccess(provider, expectedType));
    }

    public <T extends DataAccess> Optional<T> getDataAccess(String identifier, Class<T> expectedType) {
        if (expectedType == null) return Optional.empty();
        return getDatabaseProvider(identifier).flatMap(provider -> typedAccess(provider, expectedType));
    }

    public Optional<MessagingDatabaseProvider> registerRedisMessagingProvider(String identifier) {
        return registerRedisMessagingProvider(identifier, DataProviderConnections.REDIS_MESSAGING);
    }

    public Optional<MessagingDatabaseProvider> registerRedisMessagingProvider(
            String identifier, String connectionName) {
        Optional<DatabaseProvider> provider = registerDatabaseConnection(
                identifier, DatabaseType.REDIS_MESSAGING, connectionName);
        if (provider.orElse(null) instanceof MessagingDatabaseProvider messaging) return Optional.of(messaging);
        Connection removed = connections.remove(identifier);
        release(removed, identifier);
        return Optional.empty();
    }

    public Optional<MessagingDataAccess> registerRedisMessagingDataAccess(String identifier, String connectionName) {
        return registerRedisMessagingProvider(identifier, connectionName).map(MessagingDatabaseProvider::getDataAccess);
    }

    public <T extends DataAccess> Optional<T> registerRedisMessagingDataAccess(
            String identifier, Class<T> expectedType) {
        return registerRedisMessagingDataAccess(identifier, DataProviderConnections.REDIS_MESSAGING, expectedType);
    }

    public <T extends DataAccess> Optional<T> registerRedisMessagingDataAccess(
            String identifier, String connectionName, Class<T> expectedType) {
        return registerRedisMessagingProvider(identifier, connectionName)
                .flatMap(provider -> typedAccess(provider, expectedType));
    }

    public Optional<ORMContext> createMySqlOrmContext(
            String identifier, String connectionName, Class<?>... entityClasses) {
        return registerDatabaseConnection(identifier, DatabaseType.MYSQL, connectionName)
                .flatMap(ignored -> createOrmContext(identifier, entityClasses));
    }

    public Optional<ORMContext> createPlayerOrmContext(String identifier, Class<?>... entityClasses) {
        return createMySqlOrmContext(identifier, DataProviderConnections.PLAYER_DATA_RW, entityClasses);
    }

    public Optional<ORMContext> createPlayerOrmContext(Class<?>... entityClasses) {
        return createPlayerOrmContext(DEFAULT_PLAYER_ORM_IDENTIFIER, entityClasses);
    }

    public Optional<ORMContext> createSystemOrmContext(String identifier, Class<?>... entityClasses) {
        return createMySqlOrmContext(identifier, DataProviderConnections.SYSTEM_DATA_RW, entityClasses);
    }

    public Optional<ORMContext> createSystemOrmContext(Class<?>... entityClasses) {
        return createSystemOrmContext(DEFAULT_SYSTEM_ORM_IDENTIFIER, entityClasses);
    }

    public Optional<ORMContext> createOrmContext(String identifier, Class<?>... entityClasses) {
        if (!hasText(identifier) || !validEntityClasses(entityClasses)) return Optional.empty();
        DatabaseProvider provider = getDatabaseProvider(identifier).orElse(null);
        if (!(provider instanceof RelationalDatabaseProvider relational)) return Optional.empty();
        try {
            DataSource dataSource = relational.getDataSource();
            if (dataSource == null) return Optional.empty();
            ORMContext context = api().orElseThrow().createOrmContext(
                    dataSource, ormLogger, schemaMode(), entityClasses);
            ORMContext previous = ormContexts.put(identifier, context);
            lastOrmIdentifier = identifier;
            shutdownQuietly(identifier, previous);
            return Optional.of(context);
        } catch (Exception failure) {
            logger.error("Failed to create ORM context '" + identifier + "'", failure);
            return Optional.empty();
        }
    }

    public Optional<ORMContext> createORMContext(String identifier, Class<?>... entityClasses) {
        return createOrmContext(identifier, entityClasses);
    }

    public Optional<ORMContext> getOrmContext() {
        return lastOrmIdentifier == null ? Optional.empty() : getOrmContext(lastOrmIdentifier);
    }

    public Optional<ORMContext> getOrmContext(String identifier) {
        return hasText(identifier) ? Optional.ofNullable(ormContexts.get(identifier)) : Optional.empty();
    }

    public Optional<ORMContext> getORMContext() { return getOrmContext(); }

    public synchronized void closeAllDataResources() {
        quiesce();
        Throwable failure = null;
        for (ORMContext context : ormContexts.values()) {
            try { context.shutdown(); }
            catch (Throwable current) { failure = append(failure, current); }
        }
        closeScope();
        connections.clear();
        ormContexts.clear();
        lastOrmIdentifier = null;
        initialized = false;
        if (!fixedFacade) boundApi = null;
        state = FeatureResourceState.CLOSED;
        if (failure != null) throwUnchecked(failure);
    }

    public void closeAllConnections() { closeAllDataResources(); }
    public int getActiveConnectionCount() { return connections.size(); }
    public int getActiveConnCount() { return getActiveConnectionCount(); }

    private Optional<DataProviderAPI> api() {
        DataProviderAPI current = boundApi;
        if (current != null) return Optional.of(current);
        try {
            DataProviderAPI raw = apiSupplier.get();
            DataProviderAPI bound = raw == null ? null : raw.forPlugin(hostOwner);
            if (bound != null) boundApi = bound;
            return Optional.ofNullable(bound);
        } catch (RuntimeException failure) {
            logger.warn("DataProviderAPI is unavailable", failure);
            return Optional.empty();
        }
    }

    private boolean isReady() {
        requireOpen();
        return featureName != null && (initialized || initializeBoundFeature());
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Data manager is " + state + " for feature '" + featureName + "'");
        }
    }

    private <T extends DataAccess> Optional<T> typedAccess(DatabaseProvider provider, Class<T> expectedType) {
        if (provider == null || expectedType == null) return Optional.empty();
        try {
            DataAccess access = provider.getDataAccess();
            return expectedType.isInstance(access) ? Optional.of(expectedType.cast(access)) : Optional.empty();
        } catch (RuntimeException failure) {
            logger.warn("Failed to obtain typed database access", failure);
            return Optional.empty();
        }
    }

    private void release(Connection connection, String identifier) {
        if (connection != null) unregister(connection.type, connection.connectionName, identifier);
    }

    private void unregister(DatabaseType type, String connectionName, String identifier) {
        try { if (scope != null) scope.unregisterDatabase(type, connectionName); }
        catch (Exception failure) { logger.warn("Failed to unregister connection '" + identifier + "'", failure); }
    }

    private void closeScope() {
        DataProviderScope current = scope;
        scope = null;
        if (current == null) return;
        try { current.close(); }
        catch (RuntimeException failure) { logger.warn("Failed to close DataProvider scope", failure); }
    }

    private void shutdownQuietly(String identifier, ORMContext context) {
        if (context == null) return;
        try { context.shutdown(); }
        catch (RuntimeException failure) { logger.warn("Failed to replace ORM context '" + identifier + "'", failure); }
    }

    private boolean hasActiveResources() { return !connections.isEmpty() || !ormContexts.isEmpty(); }
    private String schemaMode() {
        String configured = schemaModeSupplier.get();
        return hasText(configured) ? configured.trim() : "validate";
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String normalize(String value) { return hasText(value) ? value.trim() : null; }
    private static boolean validEntityClasses(Class<?>... types) {
        if (types == null || types.length == 0) return false;
        for (Class<?> type : types) if (type == null) return false;
        return true;
    }
    private static Throwable append(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E { throw (E) failure; }

    private record Connection(DatabaseType type, String connectionName, DatabaseProvider provider) { }

    private record FrameworkLoggerAdapter(FrameworkLogger logger) implements LoggerAdapter {
        @Override public void log(LogLevel level, String message, Throwable failure) {
            switch (Objects.requireNonNull(level, "level")) {
                case INFO -> logger.info(message);
                case WARN -> logger.warn(message, failure);
                case ERROR -> logger.error(message, failure);
            }
        }
    }
}
