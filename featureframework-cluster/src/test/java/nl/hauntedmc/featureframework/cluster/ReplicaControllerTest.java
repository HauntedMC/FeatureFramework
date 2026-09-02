package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.api.feature.ActivationDecision;
import nl.hauntedmc.featureframework.api.feature.FeatureActivationPhase;
import nl.hauntedmc.featureframework.api.feature.FeatureActivationPolicy;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeatureMetadata;
import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureScope;
import nl.hauntedmc.featureframework.api.feature.FeatureSuppressionReason;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMutationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicaControllerTest {
    private static final ReplicaGroupIdentity GROUP =
            new ReplicaGroupIdentity("hauntedmc", "proxyfeatures", "proxy", "proxy-01");
    private static final ConfigCompatibility COMPATIBILITY = new ConfigCompatibility("5.0.0", "1");

    @TempDir
    Path temp;

    @Test
    void reachableEmptyDatabaseRejectsFollowerEvenWhenStaleLkgExists() {
        ConfigGeneration stale = generation(7, "stale", "1", OptionalLong.empty());
        new LastKnownGoodStore(temp).save(stale);
        InMemoryRepository repository = new InMemoryRepository();
        InMemoryLeases leases = new InMemoryLeases();

        try (ReplicaController controller = follower(temp, repository, leases)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, controller::prepareBeforeHost);
            assertTrue(failure.getMessage().contains("Replica group not initialized"));
            assertEquals(0, leases.acquireCalls.get(), "followers must never acquire authority");
        }
    }

    @Test
    void databaseOutageUsesVerifiedCompatibleLkg() throws Exception {
        ConfigGeneration lkg = generation(4, "from-lkg", "1", OptionalLong.empty());
        new LastKnownGoodStore(temp).save(lkg);
        InMemoryRepository repository = new InMemoryRepository();
        repository.failLoads = true;

        try (ReplicaController controller = follower(temp, repository, new InMemoryLeases())) {
            controller.prepareBeforeHost();
            assertEquals("from-lkg", Files.readString(temp.resolve("config.yml")));
            assertEquals(ReplicaStatus.State.READY, controller.status().state());
            assertTrue(controller.status().detail().orElseThrow().contains("LKG"));
        }
    }

    @Test
    void firstLeaderPublishesGenerationOneOnlyAfterSuccessfulHostStart() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "leader-defaults");
        InMemoryRepository repository = new InMemoryRepository();
        InMemoryLeases leases = new InMemoryLeases();
        FakeHost host = new FakeHost();

        try (ReplicaController controller = leader(temp, repository, leases)) {
            controller.prepareBeforeHost();
            controller.attach(host);

            ActivationDecision beforePublish = host.activationPolicy.evaluate(
                    leaderOnlyMetadata(), FeatureActivationPhase.ACTIVATION);
            assertFalse(beforePublish.allowed());
            assertEquals(FeatureSuppressionReason.CONFIGURATION_UNAVAILABLE,
                    beforePublish.suppression().orElseThrow().reason());
            assertTrue(repository.active().isEmpty());

            controller.afterHostStarted();

            ConfigGeneration published = repository.active().orElseThrow();
            assertEquals(1L, published.manifest().generation());
            assertEquals("leader-defaults", text(published.file("config.yml")));
            assertTrue(host.activationPolicy.evaluate(
                    leaderOnlyMetadata(), FeatureActivationPhase.ACTIVATION).allowed());
            assertEquals(ReplicaStatus.State.READY, controller.status().state());
        }
    }

    @Test
    void rollbackPublishesNewMonotonicGeneration() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        ConfigGeneration generation25 = generation(25, "old", "1", OptionalLong.empty());
        ConfigGeneration generation40 = generation(40, "current", "1", OptionalLong.empty());
        repository.seed(generation25, false);
        repository.seed(generation40, true);
        Files.writeString(temp.resolve("config.yml"), "current");
        FakeHost host = new FakeHost();

        try (ReplicaController controller = leader(temp, repository, new InMemoryLeases())) {
            controller.prepareBeforeHost();
            controller.attach(host);
            controller.afterHostStarted();

            ConfigGeneration rollback = controller.rollback(25);

            assertEquals(41L, rollback.manifest().generation());
            assertEquals(25L, rollback.manifest().sourceGeneration().orElseThrow());
            assertEquals("old", Files.readString(temp.resolve("config.yml")));
            assertEquals(41L, repository.active().orElseThrow().manifest().generation());
        }
    }

    @Test
    void followerConvergesThenBacksUpAndRepairsFilesystemDrift() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        repository.seed(generation(1, "one", "1", OptionalLong.empty()), true);
        FakeHost host = new FakeHost();

        try (ReplicaController controller = follower(temp, repository, new InMemoryLeases(), Duration.ofMillis(10))) {
            controller.prepareBeforeHost();
            controller.attach(host);
            controller.afterHostStarted();

            repository.seed(generation(2, "two", "1", OptionalLong.empty()), true);
            await(() -> fileEquals(temp.resolve("config.yml"), "two"));
            assertTrue(host.reconcileCalls.get() >= 1);

            Files.writeString(temp.resolve("config.yml"), "manual-drift");
            await(() -> fileEquals(temp.resolve("config.yml"), "two")
                    && controller.status().state() == ReplicaStatus.State.DRIFTED);

            Path drift = temp.resolve(".replica/drift");
            assertTrue(Files.isDirectory(drift));
            try (var entries = Files.list(drift)) {
                assertTrue(entries.findAny().isPresent(), "drift must be backed up before repair");
            }
        }
    }

    @Test
    void incompatibleRemoteGenerationIsNotApplied() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        repository.seed(generation(1, "compatible", "1", OptionalLong.empty()), true);
        FakeHost host = new FakeHost();

        try (ReplicaController controller = follower(temp, repository, new InMemoryLeases(), Duration.ofMillis(10))) {
            controller.prepareBeforeHost();
            controller.attach(host);
            controller.afterHostStarted();

            repository.seed(generation(2, "incompatible", "2", OptionalLong.empty()), true);
            await(() -> controller.status().state() == ReplicaStatus.State.OUT_OF_SYNC);

            assertEquals("compatible", Files.readString(temp.resolve("config.yml")));
            assertTrue(controller.status().detail().orElseThrow().contains("compatibility"));
        }
    }

    @Test
    void configuredLeaderSuppressesLeaderOnlyFeaturesAfterAuthorityLoss() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        repository.seed(generation(1, "authoritative", "1", OptionalLong.empty()), true);
        Files.writeString(temp.resolve("config.yml"), "authoritative");
        InMemoryLeases leases = new InMemoryLeases();
        FakeHost host = new FakeHost();

        try (ReplicaController controller = ReplicaController.replicated(
                temp, new ReplicaNodeIdentity("proxy-01"), GROUP, COMPATIBILITY, repository, leases)
                .leaseTiming(Duration.ofMillis(100), Duration.ofMillis(10), Duration.ofMillis(20))
                .pollInterval(Duration.ofSeconds(5))
                .build()) {
            controller.prepareBeforeHost();
            controller.attach(host);
            leases.loseOnRenew = true;
            controller.afterHostStarted();

            await(() -> controller.status().state() == ReplicaStatus.State.UNAVAILABLE);
            ActivationDecision decision = host.activationPolicy.evaluate(
                    leaderOnlyMetadata(), FeatureActivationPhase.ACTIVATION);
            assertFalse(decision.allowed());
            assertEquals(FeatureSuppressionReason.AUTHORITY_UNAVAILABLE,
                    decision.suppression().orElseThrow().reason());
            assertTrue(host.reconcileCalls.get() >= 1);
        }
    }

    @Test
    void followerWritePolicyProtectsOnlyManagedPaths() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.seed(generation(1, "authoritative", "1", OptionalLong.empty()), true);
        FakeHost host = new FakeHost();

        try (ReplicaController controller = follower(temp, repository, new InMemoryLeases())) {
            controller.prepareBeforeHost();
            controller.attach(host);

            ReplicaManagedConfigurationException failure = assertThrows(
                    ReplicaManagedConfigurationException.class,
                    () -> host.mutationPolicy.checkMutation(Path.of("config.yml"), "save"));
            assertEquals("proxy-01", failure.configuredLeader());
            host.mutationPolicy.checkMutation(Path.of("local/private-state.yml"), "save");
        }
    }

    @Test
    void managedFileDefaultsNeverRecursivelyIncludeLocalDirectory() {
        ManagedFileSet files = ManagedFileSet.builder().languageFile("nl.yml").build();
        assertTrue(files.isManaged(Path.of("config.yml")));
        assertTrue(files.isManaged(Path.of("features/Vote/config.yml")));
        assertTrue(files.isManaged(Path.of("features/Vote/messages.yml")));
        assertTrue(files.isManaged(Path.of("features/Vote/nl.yml")));
        assertFalse(files.isManaged(Path.of("local/keys.yml")));
        assertFalse(files.isManaged(Path.of("features/Vote/random.yml")));
    }

    @Test
    void corruptedLkgIsRejected() throws Exception {
        ConfigGeneration generation = generation(3, "verified", "1", OptionalLong.empty());
        LastKnownGoodStore store = new LastKnownGoodStore(temp);
        store.save(generation);
        Files.writeString(temp.resolve(".replica/generations/3/files/config.yml"), "corrupt");
        assertThrows(IllegalStateException.class, store::load);
    }

    @Test
    void corruptGenerationHashIsRejectedAtConstruction() {
        byte[] content = bytes("value");
        ConfigManifestFile file = new ConfigManifestFile(
                "config.yml", "ROOT_CONFIG", ConfigHashes.sha256(bytes("other")), content.length);
        ConfigManifest manifest = new ConfigManifest(
                1, GROUP, 1, "proxy-01", "boot", 1, "5.0.0", "1", Instant.now(),
                OptionalLong.empty(), List.of(file), ConfigHashes.manifestHash(List.of(file)));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new ConfigGeneration(manifest, Map.of("config.yml", content)));
        assertTrue(failure.getMessage().contains("Hash mismatch"));
    }

    private static ReplicaController leader(
            Path directory,
            InMemoryRepository repository,
            InMemoryLeases leases
    ) {
        return ReplicaController.replicated(
                directory, new ReplicaNodeIdentity("proxy-01"), GROUP, COMPATIBILITY, repository, leases)
                .leaseTiming(Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(1))
                .pollInterval(Duration.ofSeconds(5))
                .build();
    }

    private static ReplicaController follower(
            Path directory,
            InMemoryRepository repository,
            InMemoryLeases leases
    ) {
        return follower(directory, repository, leases, Duration.ofSeconds(5));
    }

    private static ReplicaController follower(
            Path directory,
            InMemoryRepository repository,
            InMemoryLeases leases,
            Duration pollInterval
    ) {
        return ReplicaController.replicated(
                directory, new ReplicaNodeIdentity("proxy-02"), GROUP, COMPATIBILITY, repository, leases)
                .pollInterval(pollInterval)
                .build();
    }

    private static ConfigGeneration generation(
            long generation,
            String config,
            String compatibility,
            OptionalLong source
    ) {
        Map<String, byte[]> contents = Map.of("config.yml", bytes(config));
        ConfigManifestFile file = new ConfigManifestFile(
                "config.yml", "ROOT_CONFIG", ConfigHashes.sha256(contents.get("config.yml")),
                contents.get("config.yml").length);
        List<ConfigManifestFile> files = List.of(file);
        ConfigManifest manifest = new ConfigManifest(
                ConfigManifest.CURRENT_PROTOCOL_VERSION, GROUP, generation, "proxy-01", "boot-1", 1,
                "5.0.0", compatibility, Instant.parse("2026-09-02T00:00:00Z"), source,
                files, ConfigHashes.manifestHash(files));
        return new ConfigGeneration(manifest, contents);
    }

    private static FeatureMetadata leaderOnlyMetadata() {
        return new FeatureMetadata(
                FeatureId.of("leader-only"), "Leader Only", "1", java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), FeatureScope.NODE,
                FeaturePlacement.GROUP_LEADER_ONLY);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }

    private static boolean fileEquals(Path file, String expected) {
        try { return Files.exists(file) && expected.equals(Files.readString(file)); }
        catch (Exception ignored) { return false; }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "condition was not reached before timeout");
    }

    private static final class FakeHost implements ReplicaHostControl {
        private FeatureActivationPolicy activationPolicy;
        private ConfigMutationPolicy mutationPolicy;
        private final AtomicInteger reconcileCalls = new AtomicInteger();
        private volatile boolean reconcileResult = true;

        @Override
        public void installReplicaPolicies(
                FeatureActivationPolicy activationPolicy,
                ConfigMutationPolicy mutationPolicy
        ) {
            this.activationPolicy = activationPolicy;
            this.mutationPolicy = mutationPolicy;
        }

        @Override
        public boolean reconcileReplicaGraph() {
            reconcileCalls.incrementAndGet();
            return reconcileResult;
        }
    }

    private static final class InMemoryLeases implements ReplicaLeaseCoordinator {
        private final AtomicInteger acquireCalls = new AtomicInteger();
        private long nextToken = 1;
        private ReplicaAuthority current;
        private volatile boolean loseOnRenew;

        @Override
        public synchronized CompletionStage<Optional<ReplicaAuthority>> acquire(
                ReplicaGroupIdentity group,
                String owner,
                Duration ttl
        ) {
            acquireCalls.incrementAndGet();
            if (current != null) return CompletableFuture.completedFuture(Optional.empty());
            current = new ReplicaAuthority(group.authorityResource(), owner, nextToken++, Instant.now().plus(ttl));
            return CompletableFuture.completedFuture(Optional.of(current));
        }

        @Override
        public synchronized CompletionStage<Optional<ReplicaAuthority>> renew(
                ReplicaAuthority authority,
                Duration ttl
        ) {
            if (loseOnRenew || current == null || current.fencingToken() != authority.fencingToken()
                    || !current.owner().equals(authority.owner())) {
                current = null;
                return CompletableFuture.completedFuture(Optional.empty());
            }
            current = new ReplicaAuthority(
                    authority.resource(), authority.owner(), authority.fencingToken(), Instant.now().plus(ttl));
            return CompletableFuture.completedFuture(Optional.of(current));
        }

        @Override
        public synchronized CompletionStage<Boolean> release(ReplicaAuthority authority) {
            boolean owned = current != null && current.fencingToken() == authority.fencingToken()
                    && current.owner().equals(authority.owner());
            if (owned) current = null;
            return CompletableFuture.completedFuture(owned);
        }
    }

    private static final class InMemoryRepository implements ReplicaGenerationRepository {
        private final Map<Long, ConfigGeneration> values = new LinkedHashMap<>();
        private ConfigGeneration active;
        private volatile boolean failLoads;

        synchronized void seed(ConfigGeneration generation, boolean activate) {
            values.put(generation.manifest().generation(), generation);
            if (activate) active = generation;
        }

        synchronized Optional<ConfigGeneration> active() { return Optional.ofNullable(active); }

        @Override
        public synchronized CompletionStage<Optional<ConfigGeneration>> loadActive(ReplicaGroupIdentity group) {
            if (failLoads) return CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
            return CompletableFuture.completedFuture(Optional.ofNullable(active));
        }

        @Override
        public synchronized CompletionStage<Optional<ConfigGeneration>> loadGeneration(
                ReplicaGroupIdentity group,
                long generation
        ) {
            if (failLoads) return CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
            return CompletableFuture.completedFuture(Optional.ofNullable(values.get(generation)));
        }

        @Override
        public synchronized CompletionStage<ConfigGeneration> publish(
                ReplicaGroupIdentity group,
                ConfigGeneration candidate,
                long fencingToken
        ) {
            long next = active == null ? 1L : active.manifest().generation() + 1L;
            ConfigManifest source = candidate.manifest();
            ConfigManifest manifest = new ConfigManifest(
                    source.protocolVersion(), group, next, source.publisherNode(), source.publisherBootId(),
                    fencingToken, source.applicationVersion(), source.configCompatibilityVersion(), source.createdAt(),
                    source.sourceGeneration(), source.files(), source.manifestHash());
            ConfigGeneration published = new ConfigGeneration(manifest, candidate.files());
            values.put(next, published);
            active = published;
            return CompletableFuture.completedFuture(published);
        }

        @Override
        public CompletionStage<Void> recordNodeState(
                ReplicaGroupIdentity group,
                ReplicaNodeIdentity node,
                long appliedGeneration,
                ReplicaStatus.State state,
                String detail
        ) {
            return CompletableFuture.completedFuture(null);
        }
    }
}