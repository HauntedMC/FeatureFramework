package com.example.largeplugin;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileApi {
    Optional<String> displayName(UUID playerId);
}
