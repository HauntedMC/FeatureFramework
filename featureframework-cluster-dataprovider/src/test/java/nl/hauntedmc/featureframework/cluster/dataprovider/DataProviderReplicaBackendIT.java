package nl.hauntedmc.featureframework.cluster.dataprovider;

import nl.hauntedmc.dataprovider.core.database.keyvalue.impl.redis.RedisDatabase;
import nl.hauntedmc.dataprovider.core.database.relational.impl.mysql.MySQLDatabase;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.featureframework.cluster.ConfigGeneration;
import nl.hauntedmc.featureframework.cluster.ConfigHashes;
import nl.hauntedmc.featureframework.cluster.ConfigManifest;
import nl.hauntedmc.featureframework.cluster.ConfigManifestFile;
import nl.hauntedmc.featureframework.cluster.ReplicaGroupIdentity;
import nl.hauntedmc.featureframework.cluster.ReplicaNodeIdentity;
import nl.hauntedmc.featureframework.cluster.ReplicaStatus;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class DataProviderReplicaBackendIT {
    private static final String REDIS_PASSWORD = "featureframework-it-secret";
    private static final ReplicaGroupIdentity GROUP =
            new ReplicaGroupIdentity("integration", "proxyfeatures", "proxy", "proxy-01");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("featureframework")
            .withUsername("featureframework")
            .withPassword("featureframework-secret");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    @Test
    void mysqlStorePublishesImmutableFencedGenerationsAndRollbackAsNextGeneration() throws Exception {
        MySQLDatabase database = new MySQLDatabase(mysqlConfig(), logger());
        try {
            database.connect();
            assertTrue(database.isConnected());
            DataProviderReplicaGenerationRepository repository =
                    new DataProviderReplicaGenerationRepository(database.getDataAccess());

            assertThrows(CompletionException.class,
                    () -> repository.validateSchema().toCompletableFuture().join());
            installSchema(database);
            repository.validateSchema().toCompletableFuture().join();

            ConfigGeneration first = repository.publish(GROUP,
                    candidate("one", OptionalLong.empty(), 10), 10).toCompletableFuture().join();
            assertEquals(1L, first.manifest().generation());
            assertEquals("one", text(first.file("config.yml")));

            ConfigGeneration second = repository.publish(GROUP,
                    candidate("two", OptionalLong.empty(), 10), 10).toCompletableFuture().join();
            assertEquals(2L, second.manifest().generation());
            assertEquals("two", text(second.file("config.yml")));

            assertThrows(CompletionException.class,
                    () -> repository.publish(GROUP,
                            candidate("stale", OptionalLong.empty(), 9), 9).toCompletableFuture().join());
            ConfigGeneration afterStale = repository.loadActive(GROUP).toCompletableFuture().join().orElseThrow();
            assertEquals(2L, afterStale.manifest().generation(),
                    "stale fencing token must not advance the active generation");
            assertEquals("two", text(afterStale.file("config.yml")));
            assertTrue(repository.loadGeneration(GROUP, 3).toCompletableFuture().join().isEmpty(),
                    "stale fencing token must not leave a partial immutable generation");

            ConfigGeneration rollback = repository.publish(GROUP,
                    candidate("one", OptionalLong.of(1), 10), 10).toCompletableFuture().join();
            assertEquals(3L, rollback.manifest().generation());
            assertEquals(1L, rollback.manifest().sourceGeneration().orElseThrow());
            assertEquals("one", text(rollback.file("config.yml")));

            ConfigGeneration active = repository.loadActive(GROUP).toCompletableFuture().join().orElseThrow();
            assertEquals(3L, active.manifest().generation());
            assertEquals("one", text(active.file("config.yml")));
            assertEquals("two", text(repository.loadGeneration(GROUP, 2)
                    .toCompletableFuture().join().orElseThrow().file("config.yml")));

            repository.recordNodeState(GROUP, new ReplicaNodeIdentity("proxy-02"), 3,
                    ReplicaStatus.State.READY, "converged").toCompletableFuture().join();
            Map<String, Object> row = database.getDataAccess().queryForSingle(
                    "SELECT applied_generation,status,detail FROM ff_replica_node_state "
                            + "WHERE namespace=? AND application_id=? AND group_id=? AND node_id=?",
                    GROUP.namespace(), GROUP.applicationId(), GROUP.groupId(), "proxy-02").join();
            assertEquals(3L, ((Number) row.get("applied_generation")).longValue());
            assertEquals("READY", row.get("status"));
            assertEquals("converged", row.get("detail"));
        } finally {
            database.disconnect();
        }
    }

    @Test
    void redisLeaseAdapterUsesAcquireRenewReleaseWithoutDisplacingLiveOwner() throws Exception {
        RedisDatabase database = new RedisDatabase(redisConfig(), logger());
        try {
            database.connect();
            assertTrue(database.isConnected());
            DataProviderReplicaLeaseCoordinator coordinator =
                    new DataProviderReplicaLeaseCoordinator(database.getCoordinationDataAccess());

            var first = coordinator.acquire(GROUP, "proxy-01/boot-a", Duration.ofSeconds(5))
                    .toCompletableFuture().join().orElseThrow();
            assertTrue(coordinator.acquire(GROUP, "proxy-01/boot-b", Duration.ofSeconds(5))
                    .toCompletableFuture().join().isEmpty());

            var renewed = coordinator.renew(first, Duration.ofSeconds(5))
                    .toCompletableFuture().join().orElseThrow();
            assertEquals(first.fencingToken(), renewed.fencingToken());
            assertEquals(first.owner(), renewed.owner());

            assertTrue(coordinator.release(renewed).toCompletableFuture().join());
            var second = coordinator.acquire(GROUP, "proxy-01/boot-b", Duration.ofSeconds(5))
                    .toCompletableFuture().join().orElseThrow();
            assertTrue(second.fencingToken() > first.fencingToken());
            assertFalse(coordinator.release(first).toCompletableFuture().join());
            assertTrue(coordinator.release(second).toCompletableFuture().join());
        } finally {
            database.disconnect();
        }
    }

    private static void installSchema(MySQLDatabase database) throws Exception {
        String schema;
        try (InputStream input = DataProviderReplicaBackendIT.class.getClassLoader()
                .getResourceAsStream("schema/mysql-v1.sql")) {
            if (input == null) throw new IllegalStateException("Missing schema/mysql-v1.sql test resource");
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder withoutComments = new StringBuilder();
        for (String line : schema.lines().toList()) {
            if (!line.stripLeading().startsWith("--")) withoutComments.append(line).append('\n');
        }
        for (String statement : withoutComments.toString().split(";")) {
            String sql = statement.trim();
            if (!sql.isEmpty()) database.getDataAccess().executeUpdate(sql).join();
        }
    }

    private static ConfigGeneration candidate(String value, OptionalLong source, long token) {
        byte[] contents = value.getBytes(StandardCharsets.UTF_8);
        ConfigManifestFile file = new ConfigManifestFile(
                "config.yml", "ROOT_CONFIG", ConfigHashes.sha256(contents), contents.length);
        List<ConfigManifestFile> files = List.of(file);
        ConfigManifest manifest = new ConfigManifest(
                ConfigManifest.CURRENT_PROTOCOL_VERSION,
                GROUP,
                1,
                "proxy-01",
                "boot-it",
                token,
                "5.0.0",
                "1",
                Instant.parse("2026-09-02T00:00:00Z"),
                source,
                files,
                ConfigHashes.manifestHash(files));
        return new ConfigGeneration(manifest, Map.of("config.yml", contents));
    }

    private static CommentedConfigurationNode mysqlConfig() throws Exception {
        CommentedConfigurationNode config = CommentedConfigurationNode.root();
        config.node("host").set(MYSQL.getHost());
        config.node("port").set(MYSQL.getMappedPort(3306));
        config.node("database").set(MYSQL.getDatabaseName());
        config.node("username").set(MYSQL.getUsername());
        config.node("password").set(MYSQL.getPassword());
        config.node("ssl_mode").set("DISABLED");
        config.node("allow_public_key_retrieval").set(true);
        config.node("pool_size").set(2);
        config.node("min_idle").set(0);
        config.node("connection_timeout_ms").set(2_000L);
        config.node("connect_timeout_ms").set(2_000);
        config.node("socket_timeout_ms").set(2_000);
        return config;
    }

    private static CommentedConfigurationNode redisConfig() throws Exception {
        CommentedConfigurationNode config = CommentedConfigurationNode.root();
        config.node("host").set(REDIS.getHost());
        config.node("port").set(REDIS.getMappedPort(6379));
        config.node("password").set(REDIS_PASSWORD);
        config.node("database").set(0);
        config.node("network_namespace").set("featureframework-it");
        config.node("pool", "connections").set(2);
        config.node("pool", "threads").set(2);
        config.node("pool", "min_idle").set(0);
        config.node("connection_timeout_ms").set(2_000);
        config.node("socket_timeout_ms").set(2_000);
        return config;
    }

    private static LoggerAdapter logger() {
        return (level, message, throwable) -> { };
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
