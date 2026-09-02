package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.api.feature.ActivationDecision;
import nl.hauntedmc.featureframework.api.feature.FeatureActivationPhase;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicaAuthorityWatchdogTest {
    private static final ReplicaGroupIdentity GROUP =
            new ReplicaGroupIdentity("hauntedmc", "proxyfeatures", "proxy", "proxy-01");
    private static final ConfigCompatibility COMPATIBILITY = new ConfigCompatibility("5.0.0", "1");

    @TempDir
    Path temp;

    @Test
    void blockedRenewalCannotKeepLeaderOnlyPlacementAlivePastSafetyCutoff() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "authoritative");
        BlockingLeaseCoordinator leases = new BlockingLeaseCoordinator();
        StaticRepository repository = new StaticRepository(generation());
        FakeHost host = new FakeHost();

        try (ReplicaController controller = ReplicaController.replicated(
                temp, new ReplicaNodeIdentity("proxy-01"), GROUP, COMPATIBILITY, repository, leases)
                .leaseTiming(Duration.ofMillis(250), Duration.ofMillis(20), Duration.ofMillis(80))
                .pollInterval(Duration.ofSeconds(5))
                .build()) {
            controller.prepareBeforeHost();
            controller.attach(host);
            controller.afterHostStarted();

            await(() -> leases.renewCalls.get() > 0);
            await(() -> controller.status().state() == ReplicaStatus.State.UNAVAILABLE);

            ActivationDecision decision = host.activationPolicy.evaluate(
                    leaderOnlyMetadata(), FeatureActivationPhase.ACTIVATION);
            assertFalse(decision.allowed());
            assertEquals(FeatureSuppressionReason.AUTHORITY_UNAVAILABLE,
                    decision.suppression().orElseThrow().reason());
            assertTrue(host.reconcileCalls.get() >= 1,
                    "authority cutoff must reconcile the live graph even while Redis renewal is blocked");

            leases.completeBlockedRenewalAsLost();
        }
    }

    private static ConfigGeneration generation() {
        byte[] contents = "authoritative".getBytes(StandardCharsets.UTF_8);
        ConfigManifestFile file = new ConfigManifestFile(
                "config.yml", "ROOT_CONFIG", ConfigHashes.sha256(contents), contents.length);
        List<ConfigManifestFile> files = List.of(file);
        ConfigManifest manifest = new ConfigManifest(
                ConfigManifest.CURRENT_PROTOCOL_VERSION, GROUP, 1, "proxy-01", "previous-boot", 1,
                "5.0.0", "1", Instant.parse("2026-09-02T00:00:00Z"), OptionalLong.empty(), files,
                ConfigHashes.manifestHash(files));
        return new ConfigGeneration(manifest, Map.of("config.yml", contents));
    }

    private static FeatureMetadata leaderOnlyMetadata() {
        return new FeatureMetadata(
                FeatureId.of("ingress"), "Ingress", "1", java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), FeatureScope.NODE,
                FeaturePlacement.GROUP_LEADER_ONLY);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(5L);
        }
        assertTrue(condition.getAsBoolean(), "condition was not reached before timeout");
    }

    private static final class FakeHost implements ReplicaHostControl {
        private nl.hauntedmc.featureframework.api.feature.FeatureActivationPolicy activationPolicy;
        private final AtomicInteger reconcileCalls = new AtomicInteger();

        @Override
        public void installReplicaPolicies(
                nl.hauntedmc.featureframework.api.feature.FeatureActivationPolicy activationPolicy,
                ConfigMutationPolicy mutationPolicy
        ) {
            this.activationPolicy = activationPolicy;
        }

        @Override
        public boolean reconcileReplicaGraph() {
            reconcileCalls.incrementAndGet();
            return true;
        }
    }

    private static final class BlockingLeaseCoordinator implements ReplicaLeaseCoordinator {
        private final AtomicInteger renewCalls = new AtomicInteger();
        private final CompletableFuture<Optional<ReplicaAuthority>> blockedRenewal = new CompletableFuture<>();
        private ReplicaAuthority current;

        @Override
        public synchronized CompletionStage<Optional<ReplicaAuthority>> acquire(
                ReplicaGroupIdentity group,
                String owner,
                Duration ttl
        ) {
            if (current != null) return CompletableFuture.completedFuture(Optional.empty());
            current = new ReplicaAuthority(group.authorityResource(), owner, 1, Instant.now().plus(ttl));
            return CompletableFuture.completedFuture(Optional.of(current));
        }

        @Override
        public CompletionStage<Optional<ReplicaAuthority>> renew(ReplicaAuthority authority, Duration ttl) {
            renewCalls.incrementAndGet();
            return blockedRenewal;
        }

        @Override
        public synchronized CompletionStage<Boolean> release(ReplicaAuthority authority) {
            current = null;
            return CompletableFuture.completedFuture(true);
        }

        void completeBlockedRenewalAsLost() {
            current = null;
            blockedRenewal.complete(Optional.empty());
        }
    }

    private static final class StaticRepository implements ReplicaGenerationRepository {
        private final ConfigGeneration active;

        private StaticRepository(ConfigGeneration active) {
            this.active = active;
        }

        @Override
        public CompletionStage<Optional<ConfigGeneration>> loadActive(ReplicaGroupIdentity group) {
            return CompletableFuture.completedFuture(Optional.of(active));
        }

        @Override
        public CompletionStage<Optional<ConfigGeneration>> loadGeneration(
                ReplicaGroupIdentity group,
                long generation
        ) {
            return CompletableFuture.completedFuture(generation == active.manifest().generation()
                    ? Optional.of(active) : Optional.empty());
        }

        @Override
        public CompletionStage<ConfigGeneration> publish(
                ReplicaGroupIdentity group,
                ConfigGeneration candidate,
                long fencingToken
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
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
