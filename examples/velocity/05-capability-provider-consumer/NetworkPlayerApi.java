package com.example.network.api;

import java.util.Optional;
import java.util.UUID;

public interface NetworkPlayerApi {
    Optional<String> currentServer(UUID playerId);
}
