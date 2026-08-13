package com.example.rollouts;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public, backend-agnostic routing behavior for other proxy features and plugins. */
public interface RolloutRoutingApi {
    RouteDecision route(UUID playerId, String requestedServer);

    Map<String, BackendHealth> healthSnapshot();

    record RouteDecision(Optional<String> target, String reason) {
        public static RouteDecision allow(String target, String reason) {
            return new RouteDecision(Optional.of(target), reason);
        }

        public static RouteDecision deny(String reason) {
            return new RouteDecision(Optional.empty(), reason);
        }
    }

    record BackendHealth(String server, boolean healthy, int online, Instant observedAt) { }
}
