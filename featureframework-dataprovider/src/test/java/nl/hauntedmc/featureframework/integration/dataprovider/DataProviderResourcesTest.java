package nl.hauntedmc.featureframework.integration.dataprovider;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataProviderResourcesTest {
    private final Object host = new Object();

    @Test
    void strictRegistrationTracksConnectedProviderAndCleansItUp() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        DataProviderResources manager = manager(api);

        manager.initializeForFeature("Queue");

        assertSame(provider, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        assertEquals(1, manager.getActiveConnCount());
        manager.closeAllConnections();

        verify(scope).registerDatabaseOrThrow(DatabaseType.MYSQL, "default");
        verify(scope).close();
        assertEquals(0, manager.getActiveConnCount());
    }

    @Test
    void registrationFailureIsRejectedAndStableDisconnectedHandleIsRetained() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider disconnected = mock(DatabaseProvider.class);
        when(disconnected.isConnected()).thenReturn(false);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default"))
                .thenThrow(new IllegalStateException("missing configuration"))
                .thenReturn(disconnected);
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
        assertSame(disconnected, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        assertSame(disconnected, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        verify(scope, times(2)).registerDatabaseOrThrow(DatabaseType.MYSQL, "default");
        verify(scope, never()).unregisterDatabase(DatabaseType.MYSQL, "default");
    }

    @Test
    void typedDataAccessUsesTheProviderHandle() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        MessagingDatabaseProvider provider = mock(MessagingDatabaseProvider.class);
        MessagingDataAccess access = mock(MessagingDataAccess.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataAccess()).thenReturn(access);
        when(scope.registerDatabaseOrThrow(DatabaseType.REDIS_MESSAGING, "hauntedmc")).thenReturn(provider);
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        Optional<MessagingDataAccess> result = manager.registerRedisMessagingDataAccess("redis", "hauntedmc");

        assertSame(access, result.orElseThrow());
        verify(scope).registerDatabaseOrThrow(DatabaseType.REDIS_MESSAGING, "hauntedmc");
    }

    @Test
    void ormContextsUseTheBoundApiAndRelationalDataSource() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext ormContext = mock(ORMContext.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        when(api.createOrmContext(same(dataSource), any(), eq("validate"), eq(String.class))).thenReturn(ormContext);
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        assertSame(ormContext, manager.createORMContext("main", String.class).orElseThrow());
        manager.closeAllConnections();

        verify(api).createOrmContext(same(dataSource), any(), eq("validate"), eq(String.class));
        verify(ormContext).shutdown();
    }

    @Test
    void nonRelationalDataSourcesDoNotCreateOrmContexts() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.REDIS, "default")).thenReturn(provider);
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        assertTrue(manager.registerConnection("cache", DatabaseType.REDIS, "default").isPresent());
        assertTrue(manager.createORMContext("cache", String.class).isEmpty());
    }

    @Test
    void temporarilyDisconnectedProviderRemainsRegisteredUntilFeatureCleanup() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(false);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        assertSame(provider, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        assertSame(provider, manager.getDataProvider("main").orElseThrow());
        assertSame(provider, manager.registerConnection("main", DatabaseType.MYSQL, "default").orElseThrow());
        assertEquals(1, manager.getActiveConnCount());
        verify(scope).registerDatabaseOrThrow(DatabaseType.MYSQL, "default");
        verify(scope, never()).unregisterDatabase(DatabaseType.MYSQL, "default");

        manager.closeAllConnections();
        verify(scope).close();
    }

    @Test
    void cleanupContinuesWhenDataProviderRejectsUnregistration() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DataProviderScope scope = scope(api);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL, "default")).thenReturn(provider);
        doThrow(new IllegalStateException("closed")).when(scope).close();
        DataProviderResources manager = manager(api);
        manager.initializeForFeature("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        manager.closeAllConnections();

        assertEquals(0, manager.getActiveConnCount());
        verify(scope).close();
    }

    private static DataProviderScope scope(DataProviderAPI api) {
        DataProviderScope scope = mock(DataProviderScope.class);
        when(api.scope(anyString())).thenReturn(scope);
        return scope;
    }

    private DataProviderResources manager(DataProviderAPI api) {
        return new DataProviderResources(host, api, FrameworkLogger.noop(), () -> "validate");
    }
}
