package com.example.rollouts;

import nl.hauntedmc.featureframework.toolkit.io.cache.CacheValue;
import nl.hauntedmc.featureframework.toolkit.io.cache.FileCacheStore;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe because Redis callbacks and Velocity events run on different executors. */
final class BackendHealthStore {
    private final ConcurrentHashMap<String, RolloutRoutingApi.BackendHealth> current = new ConcurrentHashMap<>();
    private final FileCacheStore disk;
    private final Duration staleAfter;

    BackendHealthStore(FileCacheStore disk, Duration staleAfter) {
        this.disk = disk;
        this.staleAfter = staleAfter;
        restoreLastKnownSnapshot();
    }

    void ingest(BackendHealthMessage message) {
        if (message == null || message.server() == null || message.server().isBlank()
                || message.online() < 0 || message.observedAtEpochMillis() <= 0) return;

        String server = normalize(message.server());
        Instant observedAt = Instant.ofEpochMilli(message.observedAtEpochMillis());
        if (observedAt.isAfter(Instant.now().plusSeconds(30))) return;
        RolloutRoutingApi.BackendHealth replacement = new RolloutRoutingApi.BackendHealth(
                server, message.acceptingPlayers(), message.online(), observedAt);
        RolloutRoutingApi.BackendHealth stored = current.compute(server, (ignored, previous) -> previous == null
                || observedAt.isAfter(previous.observedAt()) ? replacement : previous);
        if (stored == replacement) persist(replacement);
    }

    boolean isHealthy(String server) {
        RolloutRoutingApi.BackendHealth health = current.get(normalize(server));
        return health != null && health.healthy()
                && Instant.now().isBefore(health.observedAt().plus(staleAfter));
    }

    Map<String, RolloutRoutingApi.BackendHealth> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(current));
    }

    void removeExpired() {
        Instant cutoff = Instant.now().minus(staleAfter.multipliedBy(4));
        current.entrySet().removeIf(entry -> entry.getValue().observedAt().isBefore(cutoff));
        disk.cleanupExpired();
    }

    private void restoreLastKnownSnapshot() {
        disk.listAll().forEach((server, value) -> {
            Object healthy = value.getData().get("healthy");
            Object online = value.getData().get("online");
            Object observedAt = value.getData().get("observedAt");
            if (!(healthy instanceof Boolean status) || !(online instanceof Number count)
                    || !(observedAt instanceof String timestamp)) return;
            try {
                current.put(normalize(server), new RolloutRoutingApi.BackendHealth(
                        normalize(server), status, count.intValue(), Instant.parse(timestamp)));
            } catch (RuntimeException ignored) {
                disk.remove(server);
            }
        });
    }

    private void persist(RolloutRoutingApi.BackendHealth health) {
        disk.put(health.server(), CacheValue.builder(staleAfter.multipliedBy(4).toMillis())
                .with("healthy", health.healthy())
                .with("online", health.online())
                .with("observedAt", health.observedAt().toString())
                .build());
    }

    private static String normalize(String server) {
        return server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
    }
}
