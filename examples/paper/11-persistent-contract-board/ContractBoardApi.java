package com.example.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Behavior exposed to other features; JDBC and cache types stay private to ContractBoard. */
public interface ContractBoardApi {
    CompletionStage<List<Contract>> openContracts(int limit);

    CompletionStage<Contract> post(UUID creator, String description, int reward);

    CompletionStage<ClaimResult> claim(UUID contractId, UUID playerId);

    record Contract(
            UUID id,
            UUID creator,
            String description,
            int reward,
            Instant createdAt
    ) { }

    enum ClaimResult {
        CLAIMED,
        ALREADY_CLAIMED,
        NOT_FOUND
    }
}
