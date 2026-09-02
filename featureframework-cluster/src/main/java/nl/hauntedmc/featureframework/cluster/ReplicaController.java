package nl.hauntedmc.featureframework.cluster;

import nl.hauntedmc.featureframework.api.feature.ActivationDecision;
import nl.hauntedmc.featureframework.api.feature.FeatureActivationPhase;
import nl.hauntedmc.featureframework.api.feature.FeatureActivationPolicy;
import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.api.feature.FeatureSuppressionReason;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMutationPolicy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Backend-neutral orchestration for one static replica group.
 *
 * <p>Only the manually configured leader ever acquires authority. Followers never attempt
 * acquisition and this controller never promotes a follower automatically.</p>
 */
public final class ReplicaController implements AutoCloseable {
    public static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(15);
    public static final Duration DEFAULT_RENEW_INTERVAL = Duration.ofSeconds(3);
    public static final Duration DEFAULT_SAFETY_MARGIN = Duration.ofSeconds(2);
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);

    private final ReplicaMode mode;
    private final ReplicaNodeIdentity node;
    private final ReplicaGroupIdentity group;
    private final ReplicaGenerationRepository generations;
    private final ReplicaLeaseCoordinator leases;
    private final ConfigCompatibility compatibility;
    private final ManagedFileSet managedFiles;
    private final ConfigurationMaterializer materializer;
    private final LastKnownGoodStore lkg;
    private final String bootId;
    private final String owner;
    private final Duration leaseTtl;
    private final Duration renewInterval;
    private final Duration safetyMargin;
    private final Duration pollInterval;
    private final LongSupplier nanoTime;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ReplicaAuthority> authority = new AtomicReference<>();
    private final AtomicReference<ReplicaStatus> status;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean authorityMaintenanceStarted = new AtomicBoolean();
    private final AtomicBoolean hostRuntimeStarted = new AtomicBoolean();
    private volatile long lastSuccessfulRenewalNanos;
    private volatile ConfigGeneration appliedGeneration;
    private volatile ConfigGeneration startupRollbackGeneration;
    private volatile boolean bootstrapPending;
    private volatile ReplicaHostControl host;

    private ReplicaController(Builder builder) {
        mode = builder.mode;
        node = builder.node;
        group = builder.group;
        generations = builder.generations;
        leases = builder.leases;
        compatibility = builder.compatibility;
        managedFiles = builder.managedFiles;
        materializer = new ConfigurationMaterializer(builder.dataDirectory, managedFiles);
        lkg = new LastKnownGoodStore(builder.dataDirectory);
        bootId = builder.bootId == null ? UUID.randomUUID().toString() : builder.bootId;
        owner = node.nodeId() + "/" + bootId;
        leaseTtl = builder.leaseTtl;
        renewInterval = builder.renewInterval;
        safetyMargin = builder.safetyMargin;
        pollInterval = builder.pollInterval;
        nanoTime = builder.nanoTime;
        scheduler = Executors.newScheduledThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "FeatureFramework-replica-" + node.nodeId());
            thread.setDaemon(true);
            return thread;
        });
        ReplicaRole role = role();
        status = new AtomicReference<>(new ReplicaStatus(role,
                mode == ReplicaMode.STANDALONE ? ReplicaStatus.State.STANDALONE : ReplicaStatus.State.BOOTSTRAPPING,
                Optional.empty(), OptionalLong.empty(), Optional.empty()));
    }

    public static Builder standalone(Path dataDirectory, ReplicaNodeIdentity node, ConfigCompatibility compatibility) {
        return new Builder(ReplicaMode.STANDALONE, dataDirectory, node, compatibility);
    }

    public static Builder replicated(
            Path dataDirectory,
            ReplicaNodeIdentity node,
            ReplicaGroupIdentity group,
            ConfigCompatibility compatibility,
            ReplicaGenerationRepository generations,
            ReplicaLeaseCoordinator leases
    ) {
        return new Builder(ReplicaMode.REPLICATED, dataDirectory, node, compatibility)
                .group(group).generations(generations).leases(leases);
    }

    public ReplicaMode mode() { return mode; }
    public ReplicaNodeIdentity node() { return node; }
    public Optional<ReplicaGroupIdentity> group() { return Optional.ofNullable(group); }
    public String bootId() { return bootId; }
    public ReplicaRole role() { return mode == ReplicaMode.STANDALONE ? ReplicaRole.STANDALONE
            : group.isConfiguredLeader(node) ? ReplicaRole.LEADER : ReplicaRole.FOLLOWER; }
    public ReplicaStatus status() { return status.get(); }
    public ManagedFileSet managedFiles() { return managedFiles; }

    public FeatureActivationPolicy activationPolicy() {
        return (metadata, phase) -> {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(phase, "phase");
            if (mode == ReplicaMode.STANDALONE || metadata.placement() == FeaturePlacement.ALL_NODES) {
                return ActivationDecision.allow();
            }
            if (role() == ReplicaRole.FOLLOWER) {
                return ActivationDecision.suppress(FeatureSuppressionReason.GROUP_LEADER_ONLY,
                        "Feature placement is restricted to configured leader " + group.configuredLeader());
            }
            if (authority.get() == null) {
                return ActivationDecision.suppress(FeatureSuppressionReason.AUTHORITY_UNAVAILABLE,
                        "Configured leader cannot currently prove fenced authority");
            }
            if (bootstrapPending && phase == FeatureActivationPhase.ACTIVATION) {
                return ActivationDecision.suppress(FeatureSuppressionReason.CONFIGURATION_UNAVAILABLE,
                        "Replica group has not published its first configuration generation yet");
            }
            return ActivationDecision.allow();
        };
    }

    public ConfigMutationPolicy configMutationPolicy() {
        return (relativePath, operation) -> {
            if (mode == ReplicaMode.REPLICATED && role() == ReplicaRole.FOLLOWER && managedFiles.isManaged(relativePath)) {
                throw new ReplicaManagedConfigurationException(relativePath, operation, group);
            }
        };
    }

    /** Materializes an authoritative generation or LKG before the host constructs feature contexts. */
    public synchronized void prepareBeforeHost() {
        ensureOpen();
        if (mode == ReplicaMode.STANDALONE) {
            setStatus(ReplicaStatus.State.STANDALONE, null);
            return;
        }

        if (role() == ReplicaRole.LEADER) {
            acquireAuthorityIfPossible();
            if (authority.get() != null) startAuthorityMaintenance();
        }
        ConfigGeneration remote = null;
        Throwable remoteFailure = null;
        try {
            remote = join(generations.loadActive(group)).orElse(null);
        } catch (RuntimeException failure) {
            remoteFailure = failure;
        }

        if (remote == null && remoteFailure != null) {
            remote = compatibleLkg().orElse(null);
            if (remote == null) {
                throw new IllegalStateException("Replica control-plane database is unavailable and no valid LKG exists", remoteFailure);
            }
            materializer.materialize(remote);
            appliedGeneration = remote;
            if (role() == ReplicaRole.LEADER) startAuthorityMaintenance();
            setStatus(ReplicaStatus.State.READY, "Started from verified LKG while database is unavailable");
            return;
        }

        if (remote == null) {
            if (role() == ReplicaRole.FOLLOWER) {
                throw new IllegalStateException(
                        "Replica group not initialized. Start configured leader " + group.configuredLeader() + " first.");
            }
            if (authority.get() == null) {
                throw new IllegalStateException("Configured leader cannot initialize the replica group without authority");
            }
            startAuthorityMaintenance();
            bootstrapPending = true;
            setStatus(ReplicaStatus.State.BOOTSTRAPPING, "Waiting to publish first configuration generation");
            return;
        }

        requireCompatible(remote);
        if (role() == ReplicaRole.LEADER) startAuthorityMaintenance();
        if (role() == ReplicaRole.LEADER) {
            Map<String, byte[]> local = materializer.snapshot();
            if (!local.isEmpty() && !materializer.matches(remote)) {
                startupRollbackGeneration = remote;
                appliedGeneration = remote;
                setStatus(ReplicaStatus.State.BOOTSTRAPPING,
                        "Leader local configuration will be validated as startup candidate");
                return;
            }
        }
        materializer.materialize(remote);
        appliedGeneration = remote;
        lkg.save(remote);
        setStatus(ReplicaStatus.State.READY, null);
    }

    /** Installs lifecycle and write policies on the already constructed host before host start. */
    public synchronized void attach(ReplicaHostControl host) {
        ensureOpen();
        if (this.host != null) throw new IllegalStateException("Replica controller is already attached to a host");
        ReplicaHostControl candidate = Objects.requireNonNull(host, "host");
        candidate.installReplicaPolicies(activationPolicy(), configMutationPolicy());
        this.host = candidate;
    }

    /** Completes first-generation/startup-candidate publication and starts polling. */
    public synchronized void afterHostStarted() {
        ensureOpen();
        if (host == null) throw new IllegalStateException("Attach the FeatureFramework host before afterHostStarted()");
        if (!hostRuntimeStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("Replica host runtime has already been started");
        }
        if (mode == ReplicaMode.STANDALONE) return;
        if (role() == ReplicaRole.LEADER && (bootstrapPending || startupRollbackGeneration != null)) {
            publishStartupCandidate();
        }
        long pollMillis = Math.max(1L, pollInterval.toMillis());
        scheduler.scheduleAtFixedRate(this::safePollTick, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    /** Explicit leader transaction boundary for runtime configuration edits. */
    public synchronized ConfigGeneration publishCurrentConfiguration() {
        ensureLeaderAuthority();
        if (host == null) throw new IllegalStateException("Replica host is not attached");
        Map<String, byte[]> current = materializer.snapshot();
        ConfigGeneration previous = appliedGeneration;
        if (!host.reconcileReplicaGraph()) {
            if (previous != null) materializer.materialize(previous);
            if (previous != null) host.reconcileReplicaGraph();
            throw new IllegalStateException("Candidate configuration failed host graph validation");
        }
        try {
            ConfigGeneration published = join(generations.publish(group,
                    candidate(current, OptionalLong.empty()), authority.get().fencingToken()));
            acceptPublished(published);
            return published;
        } catch (RuntimeException failure) {
            if (previous != null) {
                materializer.materialize(previous);
                host.reconcileReplicaGraph();
            }
            throw failure;
        }
    }

    /** Rollback is a new immutable generation; the active pointer never moves backwards. */
    public synchronized ConfigGeneration rollback(long sourceGeneration) {
        ensureLeaderAuthority();
        ConfigGeneration source = join(generations.loadGeneration(group, sourceGeneration))
                .orElseThrow(() -> new IllegalArgumentException("Unknown configuration generation " + sourceGeneration));
        requireCompatible(source);
        ConfigGeneration previous = appliedGeneration;
        materializer.materialize(source);
        if (host != null && !host.reconcileReplicaGraph()) {
            if (previous != null) materializer.materialize(previous);
            if (previous != null) host.reconcileReplicaGraph();
            throw new IllegalStateException("Rollback source generation is not valid for the running host");
        }
        try {
            ConfigGeneration published = join(generations.publish(group,
                    candidate(source.files(), OptionalLong.of(sourceGeneration)), authority.get().fencingToken()));
            acceptPublished(published);
            return published;
        } catch (RuntimeException failure) {
            if (previous != null) materializer.materialize(previous);
            if (previous != null && host != null) host.reconcileReplicaGraph();
            throw failure;
        }
    }

    private void publishStartupCandidate() {
        ensureLeaderAuthority();
        ConfigGeneration previous = startupRollbackGeneration;
        try {
            ConfigGeneration published = join(generations.publish(group,
                    candidate(materializer.snapshot(), OptionalLong.empty()), authority.get().fencingToken()));
            bootstrapPending = false;
            startupRollbackGeneration = null;
            acceptPublished(published);
            host.reconcileReplicaGraph();
        } catch (RuntimeException failure) {
            if (previous != null) {
                materializer.materialize(previous);
                host.reconcileReplicaGraph();
                appliedGeneration = previous;
            }
            bootstrapPending = true;
            setStatus(ReplicaStatus.State.UNAVAILABLE, "Failed to publish startup configuration generation");
            throw new IllegalStateException("Replica startup publication failed", failure);
        }
    }

    private void startAuthorityMaintenance() {
        if (mode != ReplicaMode.REPLICATED || role() != ReplicaRole.LEADER
                || !authorityMaintenanceStarted.compareAndSet(false, true)) return;
        long renewNanos = Math.max(1L, renewInterval.toNanos());
        long halfSafety = Math.max(1L, safetyMargin.toNanos() / 2L);
        long watchdogNanos = Math.max(1L, Math.min(renewNanos, halfSafety));
        scheduler.scheduleAtFixedRate(this::safeRenewTick, renewNanos, renewNanos, TimeUnit.NANOSECONDS);
        scheduler.scheduleAtFixedRate(
                this::safeAuthorityWatchdogTick, watchdogNanos, watchdogNanos, TimeUnit.NANOSECONDS);
    }

    private void safeRenewTick() {
        try { renewTick(); } catch (Throwable failure) { evaluateAuthorityWatchdog(failure); }
    }

    private void renewTick() {
        if (closed.get() || mode != ReplicaMode.REPLICATED || role() != ReplicaRole.LEADER) return;
        ReplicaAuthority current = authority.get();
        if (current == null) {
            acquireAuthorityIfPossible();
            if (authority.get() != null && host != null) host.reconcileReplicaGraph();
            return;
        }
        Optional<ReplicaAuthority> renewed = join(leases.renew(current, leaseTtl));
        if (closed.get()) return;
        if (renewed.isEmpty()) {
            if (authority.compareAndSet(current, null)) {
                setStatus(ReplicaStatus.State.UNAVAILABLE, "Configured leader lost fenced authority");
                ReplicaHostControl currentHost = host;
                if (currentHost != null) currentHost.reconcileReplicaGraph();
            }
            return;
        }
        lastSuccessfulRenewalNanos = nanoTime.getAsLong();
        authority.set(renewed.get());
        refreshStatusAfterAuthorityProof();
    }

    private void safeAuthorityWatchdogTick() {
        try { evaluateAuthorityWatchdog(null); } catch (Throwable ignored) { }
    }

    private void evaluateAuthorityWatchdog(Throwable failure) {
        if (closed.get() || mode != ReplicaMode.REPLICATED || role() != ReplicaRole.LEADER) return;
        ReplicaAuthority current = authority.get();
        if (current == null) return;
        long safeNanos = leaseTtl.minus(safetyMargin).toNanos();
        long elapsed = Math.max(0L, nanoTime.getAsLong() - lastSuccessfulRenewalNanos);
        if (elapsed >= safeNanos && authority.compareAndSet(current, null)) {
            String detail = "Authority renewal was not proven inside the TTL safety window";
            if (failure != null) detail += ": " + failure;
            setStatus(ReplicaStatus.State.UNAVAILABLE, detail);
            ReplicaHostControl currentHost = host;
            if (currentHost != null) currentHost.reconcileReplicaGraph();
        }
    }

    private void safePollTick() {
        try { pollTick(); }
        catch (Throwable failure) {
            setStatus(ReplicaStatus.State.OUT_OF_SYNC, "Replica poll failed: " + failure.getMessage());
        }
    }

    private synchronized void pollTick() {
        if (closed.get() || mode != ReplicaMode.REPLICATED) return;
        ConfigGeneration remote = join(generations.loadActive(group)).orElse(null);
        if (remote == null) return;
        if (!compatibility.isCompatible(remote.manifest())) {
            setStatus(ReplicaStatus.State.OUT_OF_SYNC,
                    "Active generation requires config compatibility "
                            + remote.manifest().configCompatibilityVersion());
            return;
        }
        long applied = appliedGeneration == null ? 0L : appliedGeneration.manifest().generation();
        if (remote.manifest().generation() > applied) {
            applyRemote(remote);
        } else if (role() == ReplicaRole.FOLLOWER && appliedGeneration != null
                && !materializer.matches(appliedGeneration)) {
            materializer.backupDrift();
            materializer.materialize(appliedGeneration);
            if (host != null && !host.reconcileReplicaGraph()) {
                setStatus(ReplicaStatus.State.OUT_OF_SYNC,
                        "Follower drift repair could not reload previous graph");
                return;
            }
            setStatus(ReplicaStatus.State.DRIFTED,
                    "Follower filesystem drift was backed up and repaired");
        }
    }

    private void applyRemote(ConfigGeneration remote) {
        ConfigGeneration previous = appliedGeneration;
        if (previous != null && !materializer.matches(previous) && role() == ReplicaRole.FOLLOWER) {
            materializer.backupDrift();
        }
        materializer.materialize(remote);
        if (host != null && !host.reconcileReplicaGraph()) {
            if (previous != null) {
                materializer.materialize(previous);
                host.reconcileReplicaGraph();
            }
            setStatus(ReplicaStatus.State.OUT_OF_SYNC,
                    "Rejected generation " + remote.manifest().generation());
            return;
        }
        appliedGeneration = remote;
        lkg.save(remote);
        setStatus(ReplicaStatus.State.READY, null);
        recordNodeStateBestEffort();
    }

    private void acquireAuthorityIfPossible() {
        if (closed.get() || mode != ReplicaMode.REPLICATED || role() != ReplicaRole.LEADER) return;
        Optional<ReplicaAuthority> acquired = join(leases.acquire(group, owner, leaseTtl));
        if (closed.get()) return;
        authority.set(acquired.orElse(null));
        if (acquired.isPresent()) {
            lastSuccessfulRenewalNanos = nanoTime.getAsLong();
            refreshStatusAfterAuthorityProof();
        } else {
            refreshStatusAuthority();
        }
    }

    private void refreshStatusAfterAuthorityProof() {
        ReplicaStatus current = status.get();
        if (current.state() == ReplicaStatus.State.UNAVAILABLE && !bootstrapPending) {
            setStatus(ReplicaStatus.State.READY, "Fenced authority restored");
        } else {
            refreshStatusAuthority();
        }
    }

    private ConfigGeneration candidate(Map<String, byte[]> files, OptionalLong sourceGeneration) {
        List<ConfigManifestFile> manifestFiles = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String kind = fileKind(entry.getKey());
            manifestFiles.add(new ConfigManifestFile(entry.getKey(), kind,
                    ConfigHashes.sha256(entry.getValue()), entry.getValue().length));
        });
        String manifestHash = ConfigHashes.manifestHash(manifestFiles);
        ReplicaAuthority current = authority.get();
        long token = current == null ? 1L : current.fencingToken();
        ConfigManifest manifest = new ConfigManifest(
                ConfigManifest.CURRENT_PROTOCOL_VERSION, group, 1L, node.nodeId(), bootId, token,
                compatibility.applicationVersion(), compatibility.configCompatibilityVersion(), Instant.now(),
                sourceGeneration, manifestFiles, manifestHash);
        return new ConfigGeneration(manifest, files);
    }

    private static String fileKind(String path) {
        if ("config.yml".equals(path)) return "ROOT_CONFIG";
        if (path.endsWith("/config.yml")) return "FEATURE_CONFIG";
        if (path.endsWith("/messages.yml")) return "FEATURE_MESSAGES";
        return "FEATURE_LANGUAGE";
    }

    private Optional<ConfigGeneration> compatibleLkg() {
        try {
            return lkg.load().filter(value -> compatibility.isCompatible(value.manifest()));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private void requireCompatible(ConfigGeneration generation) {
        generation.verify();
        if (!compatibility.isCompatible(generation.manifest())) {
            throw new IllegalStateException("Configuration compatibility mismatch: running "
                    + compatibility.configCompatibilityVersion() + ", generation "
                    + generation.manifest().configCompatibilityVersion());
        }
    }

    private void acceptPublished(ConfigGeneration generation) {
        requireCompatible(generation);
        appliedGeneration = generation;
        lkg.save(generation);
        setStatus(ReplicaStatus.State.READY, null);
        recordNodeStateBestEffort();
    }

    private void ensureLeaderAuthority() {
        ensureOpen();
        if (mode != ReplicaMode.REPLICATED || role() != ReplicaRole.LEADER) {
            throw new IllegalStateException(
                    "Only the configured replica-group leader may publish configuration");
        }
        if (authority.get() == null) {
            throw new IllegalStateException("Configured leader does not hold fenced authority");
        }
    }

    private void recordNodeStateBestEffort() {
        if (mode != ReplicaMode.REPLICATED || appliedGeneration == null) return;
        try {
            generations.recordNodeState(group, node, appliedGeneration.manifest().generation(),
                    status.get().state(), status.get().detail().orElse(null));
        } catch (RuntimeException ignored) { }
    }

    private void setStatus(ReplicaStatus.State state, String detail) {
        long generation = appliedGeneration == null ? 0L : appliedGeneration.manifest().generation();
        status.set(new ReplicaStatus(role(), state, Optional.ofNullable(authority.get()),
                generation <= 0 ? OptionalLong.empty() : OptionalLong.of(generation),
                Optional.ofNullable(detail)));
    }

    private void refreshStatusAuthority() {
        ReplicaStatus current = status.get();
        status.set(new ReplicaStatus(role(), current.state(), Optional.ofNullable(authority.get()),
                current.appliedGeneration(), current.detail()));
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Replica controller is closed");
    }

    private static <T> T join(java.util.concurrent.CompletionStage<T> stage) {
        return Objects.requireNonNull(stage, "stage").toCompletableFuture().join();
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdownNow();
        ConfigGeneration rollback = startupRollbackGeneration;
        startupRollbackGeneration = null;
        if (rollback != null) {
            try {
                materializer.materialize(rollback);
                appliedGeneration = rollback;
            } catch (RuntimeException ignored) { }
        }
        ReplicaAuthority current = authority.getAndSet(null);
        if (current != null && leases != null) {
            try { join(leases.release(current)); } catch (RuntimeException ignored) { }
        }
    }

    public static final class Builder {
        private final ReplicaMode mode;
        private final Path dataDirectory;
        private final ReplicaNodeIdentity node;
        private final ConfigCompatibility compatibility;
        private ReplicaGroupIdentity group;
        private ReplicaGenerationRepository generations;
        private ReplicaLeaseCoordinator leases;
        private ManagedFileSet managedFiles = ManagedFileSet.defaults();
        private String bootId;
        private Duration leaseTtl = DEFAULT_LEASE_TTL;
        private Duration renewInterval = DEFAULT_RENEW_INTERVAL;
        private Duration safetyMargin = DEFAULT_SAFETY_MARGIN;
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;
        private LongSupplier nanoTime = System::nanoTime;

        private Builder(
                ReplicaMode mode,
                Path dataDirectory,
                ReplicaNodeIdentity node,
                ConfigCompatibility compatibility
        ) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
            this.node = Objects.requireNonNull(node, "node");
            this.compatibility = Objects.requireNonNull(compatibility, "compatibility");
        }

        private Builder group(ReplicaGroupIdentity value) { group = value; return this; }
        private Builder generations(ReplicaGenerationRepository value) { generations = value; return this; }
        private Builder leases(ReplicaLeaseCoordinator value) { leases = value; return this; }
        public Builder managedFiles(ManagedFileSet value) {
            managedFiles = Objects.requireNonNull(value, "managedFiles");
            return this;
        }
        public Builder bootId(String value) {
            bootId = Objects.requireNonNull(value, "bootId").trim();
            if (bootId.isEmpty()) throw new IllegalArgumentException("bootId must not be blank");
            return this;
        }
        public Builder leaseTiming(Duration ttl, Duration renew, Duration safety) {
            leaseTtl = positive(ttl, "ttl");
            renewInterval = positive(renew, "renew");
            safetyMargin = positive(safety, "safety");
            if (renewInterval.compareTo(leaseTtl) >= 0) {
                throw new IllegalArgumentException("renew interval must be less than TTL");
            }
            if (safetyMargin.compareTo(leaseTtl) >= 0) {
                throw new IllegalArgumentException("safety margin must be less than TTL");
            }
            return this;
        }
        public Builder pollInterval(Duration value) {
            pollInterval = positive(value, "pollInterval");
            return this;
        }
        Builder nanoTime(LongSupplier value) {
            nanoTime = Objects.requireNonNull(value, "nanoTime");
            return this;
        }

        public ReplicaController build() {
            if (mode == ReplicaMode.REPLICATED) {
                Objects.requireNonNull(group, "group");
                Objects.requireNonNull(generations, "generations");
                Objects.requireNonNull(leases, "leases");
            }
            return new ReplicaController(this);
        }

        private static Duration positive(Duration value, String field) {
            Duration duration = Objects.requireNonNull(value, field);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return duration;
        }
    }
}
