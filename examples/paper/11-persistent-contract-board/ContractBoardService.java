package com.example.contracts;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Domain service used by commands, listeners, and external capability consumers. */
final class ContractBoardService implements ContractBoardApi {
    private final ContractBoardFeature feature;
    private final ContractRepository repository;
    private final ContractSnapshotCache cache;
    private final Duration cacheTtl;
    private final int maxReward;
    private final int snapshotSize;
    private final AtomicBoolean closed = new AtomicBoolean();

    ContractBoardService(
            ContractBoardFeature feature,
            ContractRepository repository,
            ContractSnapshotCache cache,
            Duration cacheTtl,
            int maxReward,
            int snapshotSize
    ) {
        this.feature = feature;
        this.repository = repository;
        this.cache = cache;
        this.cacheTtl = cacheTtl;
        this.maxReward = maxReward;
        this.snapshotSize = snapshotSize;
    }

    @Override
    public CompletionStage<List<Contract>> openContracts(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, snapshotSize));
        var hot = cache.current(cacheTtl);
        if (hot.isPresent()) {
            return CompletableFuture.completedFuture(hot.orElseThrow().stream().limit(boundedLimit).toList());
        }
        return submit(() -> repository.findOpen(snapshotSize))
                .thenApply(contracts -> {
                    cache.replace(contracts);
                    return contracts.stream().limit(boundedLimit).toList();
                });
    }

    @Override
    public CompletionStage<Contract> post(UUID creator, String description, int reward) {
        Objects.requireNonNull(creator, "creator");
        String normalized = Objects.requireNonNull(description, "description").trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "description must contain 1-160 characters"));
        }
        if (reward < 1 || reward > maxReward) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "reward must be between 1 and " + maxReward));
        }
        return submit(() -> repository.insert(creator, normalized, reward))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) cache.invalidate();
                });
    }

    @Override
    public CompletionStage<ClaimResult> claim(UUID contractId, UUID playerId) {
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> repository.claim(contractId, playerId))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) cache.invalidate();
                });
    }

    void refreshSnapshot() {
        if (closed.get()) return;
        try {
            cache.replace(repository.findOpen(snapshotSize));
        } catch (RuntimeException failure) {
            if (!closed.get()) feature.logger().warn("Contract snapshot refresh failed: " + failure.getMessage());
        }
    }

    int lastKnownOpenCount() {
        return cache.lastKnownOpenCount();
    }

    void close() {
        closed.set(true);
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> work) {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("ContractBoard is stopping"));
        return feature.resources().getTaskManager().supplyAsync(() -> {
            if (closed.get()) throw new IllegalStateException("ContractBoard is stopping");
            return work.get();
        });
    }
}
