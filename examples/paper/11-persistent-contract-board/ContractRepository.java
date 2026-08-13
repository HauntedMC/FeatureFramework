package com.example.contracts;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small JDBC adapter. It has no Bukkit dependencies and is straightforward to integration-test. */
final class ContractRepository {
    private final DataSource dataSource;

    ContractRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void ensureSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS network_contract (
                    contract_id VARCHAR(36) PRIMARY KEY,
                    creator_id VARCHAR(36) NOT NULL,
                    description VARCHAR(160) NOT NULL,
                    reward INT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    claimed_by VARCHAR(36),
                    created_at TIMESTAMP(3) NOT NULL,
                    claimed_at TIMESTAMP(3),
                    INDEX contract_status_created (status, created_at)
                )
                """;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not prepare the contract-board schema", failure);
        }
    }

    ContractBoardApi.Contract insert(UUID creator, String description, int reward) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String sql = """
                INSERT INTO network_contract
                    (contract_id, creator_id, description, reward, status, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setString(2, creator.toString());
            statement.setString(3, description);
            statement.setInt(4, reward);
            statement.setTimestamp(5, Timestamp.from(createdAt));
            statement.executeUpdate();
            return new ContractBoardApi.Contract(id, creator, description, reward, createdAt);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not post contract " + id, failure);
        }
    }

    List<ContractBoardApi.Contract> findOpen(int limit) {
        String sql = """
                SELECT contract_id, creator_id, description, reward, created_at
                FROM network_contract
                WHERE status = 'OPEN'
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<ContractBoardApi.Contract> contracts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    contracts.add(new ContractBoardApi.Contract(
                            UUID.fromString(rows.getString("contract_id")),
                            UUID.fromString(rows.getString("creator_id")),
                            rows.getString("description"),
                            rows.getInt("reward"),
                            rows.getTimestamp("created_at").toInstant()
                    ));
                }
            }
            return List.copyOf(contracts);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load open contracts", failure);
        }
    }

    ContractBoardApi.ClaimResult claim(UUID contractId, UUID playerId) {
        String update = """
                UPDATE network_contract
                SET status = 'CLAIMED', claimed_by = ?, claimed_at = ?
                WHERE contract_id = ? AND status = 'OPEN'
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, playerId.toString());
                statement.setTimestamp(2, Timestamp.from(Instant.now()));
                statement.setString(3, contractId.toString());
                if (statement.executeUpdate() == 1) return ContractBoardApi.ClaimResult.CLAIMED;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status FROM network_contract WHERE contract_id = ?")) {
                statement.setString(1, contractId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? ContractBoardApi.ClaimResult.ALREADY_CLAIMED
                            : ContractBoardApi.ClaimResult.NOT_FOUND;
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not claim contract " + contractId, failure);
        }
    }
}
