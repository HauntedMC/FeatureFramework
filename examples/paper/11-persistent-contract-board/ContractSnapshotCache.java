package com.example.contracts;

import nl.hauntedmc.featureframework.toolkit.io.cache.CacheValue;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Hot immutable snapshot plus a small on-disk last-known-good operational summary. */
final class ContractSnapshotCache {
    private static final String SUMMARY_KEY = "open-contract-summary";

    private final FileCacheStore disk;
    private volatile List<ContractBoardApi.Contract> openContracts = List.of();
    private volatile Instant refreshedAt = Instant.EPOCH;

    ContractSnapshotCache(FileCacheStore disk) {
        this.disk = disk;
    }

    Optional<List<ContractBoardApi.Contract>> current(Duration maxAge) {
        return Instant.now().isBefore(refreshedAt.plus(maxAge))
                ? Optional.of(openContracts)
                : Optional.empty();
    }

    int lastKnownOpenCount() {
        if (!openContracts.isEmpty()) return openContracts.size();
        CacheValue cached = disk.get(SUMMARY_KEY);
        if (cached == null) return 0;
        Object count = cached.getData().get("openCount");
        return count instanceof Number number ? number.intValue() : 0;
    }

    void replace(List<ContractBoardApi.Contract> contracts) {
        openContracts = List.copyOf(contracts);
        refreshedAt = Instant.now();
        disk.put(SUMMARY_KEY, CacheValue.builder(Duration.ofDays(7).toMillis())
                .with("openCount", contracts.size())
                .with("refreshedAt", refreshedAt.toString())
                .build());
    }

    void invalidate() {
        refreshedAt = Instant.EPOCH;
    }
}
