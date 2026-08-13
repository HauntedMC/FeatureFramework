package com.example.rollouts;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pure deterministic policy: easy to unit-test without Velocity, Redis, or a scheduler. */
final class RolloutPolicy implements RolloutRoutingApi {
    private final BackendHealthStore health;
    private final String stable;
    private final String canary;
    private final String fallback;
    private final int canaryPercent;

    RolloutPolicy(
            BackendHealthStore health,
            String stable,
            String canary,
            String fallback,
            int canaryPercent
    ) {
        this.health = health;
        this.stable = normalize(stable);
        this.canary = normalize(canary);
        this.fallback = normalize(fallback);
        if (canaryPercent < 0 || canaryPercent > 100) {
            throw new IllegalArgumentException("routing.canary-percent must be between 0 and 100");
        }
        this.canaryPercent = canaryPercent;
    }

    @Override
    public RouteDecision route(UUID playerId, String requestedServer) {
        if (playerId == null || requestedServer == null || requestedServer.isBlank()) {
            return RouteDecision.deny("invalid-request");
        }
        String requested = normalize(requestedServer);
        if (!requested.equals(stable)) return RouteDecision.allow(requested, "outside-rollout");

        boolean inCanary = Math.floorMod(playerId.hashCode(), 100) < canaryPercent;
        if (inCanary && health.isHealthy(canary)) return RouteDecision.allow(canary, "canary-cohort");
        if (health.isHealthy(stable)) return RouteDecision.allow(stable, "stable");
        if (health.isHealthy(canary)) return RouteDecision.allow(canary, "stable-unhealthy");
        if (health.isHealthy(fallback)) return RouteDecision.allow(fallback, "rollout-fallback");
        return RouteDecision.deny("no-fresh-healthy-target");
    }

    @Override
    public Map<String, BackendHealth> healthSnapshot() {
        return health.snapshot();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("server name must not be blank");
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
