package nl.hauntedmc.featureframework.cluster.dataprovider;

import nl.hauntedmc.dataprovider.database.relational.RelationalDataAccess;
import nl.hauntedmc.featureframework.cluster.ConfigGeneration;
import nl.hauntedmc.featureframework.cluster.ConfigManifest;
import nl.hauntedmc.featureframework.cluster.ConfigManifestFile;
import nl.hauntedmc.featureframework.cluster.ReplicaGenerationRepository;
import nl.hauntedmc.featureframework.cluster.ReplicaGroupIdentity;
import nl.hauntedmc.featureframework.cluster.ReplicaNodeIdentity;
import nl.hauntedmc.featureframework.cluster.ReplicaStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Raw-SQL MySQL generation store. It never creates or mutates schema implicitly. */
public final class DataProviderReplicaGenerationRepository implements ReplicaGenerationRepository {
    private static final Map<String, Set<String>> REQUIRED_SCHEMA = Map.of(
            "ff_replica_group", Set.of(
                    "namespace", "application_id", "group_id", "active_generation", "active_manifest_hash",
                    "highest_fencing_token", "updated_at"),
            "ff_config_generation", Set.of(
                    "namespace", "application_id", "group_id", "generation", "publisher_node",
                    "publisher_boot_id", "fencing_token", "application_version", "config_compatibility_version",
                    "created_at", "source_generation", "manifest_hash"),
            "ff_config_file", Set.of(
                    "namespace", "application_id", "group_id", "generation", "path", "kind", "sha256",
                    "size", "raw_contents"),
            "ff_replica_node_state", Set.of(
                    "namespace", "application_id", "group_id", "node_id", "applied_generation", "status",
                    "detail", "last_seen")
    );

    private final RelationalDataAccess sql;

    public DataProviderReplicaGenerationRepository(RelationalDataAccess sql) {
        this.sql = Objects.requireNonNull(sql, "sql");
    }

    @Override
    public CompletionStage<Optional<ConfigGeneration>> loadActive(ReplicaGroupIdentity group) {
        Objects.requireNonNull(group, "group");
        return sql.queryForSingleOptional(
                "SELECT active_generation,active_manifest_hash FROM ff_replica_group "
                        + "WHERE namespace=? AND application_id=? AND group_id=?",
                group.namespace(), group.applicationId(), group.groupId()
        ).thenCompose(row -> {
            if (row.isEmpty() || column(row.get(), "active_generation") == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
            }
            long generation = ((Number) column(row.get(), "active_generation")).longValue();
            String expectedHash = Objects.toString(column(row.get(), "active_manifest_hash"), "");
            return loadGeneration(group, generation).thenApply(loaded -> {
                ConfigGeneration active = loaded.orElseThrow(() -> new IllegalStateException(
                        "Replica group points to missing active generation " + generation));
                if (!active.manifest().manifestHash().equalsIgnoreCase(expectedHash)) {
                    throw new IllegalStateException(
                            "Replica group active manifest hash does not match generation " + generation);
                }
                return Optional.of(active);
            });
        });
    }

    @Override
    public CompletionStage<Optional<ConfigGeneration>> loadGeneration(ReplicaGroupIdentity group, long generation) {
        if (generation <= 0) throw new IllegalArgumentException("generation must be positive");
        return sql.executeTransactionally(connection -> loadGeneration(connection, group, generation));
    }

    @Override
    public CompletionStage<ConfigGeneration> publish(
            ReplicaGroupIdentity group,
            ConfigGeneration candidate,
            long fencingToken
    ) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(candidate, "candidate").verify();
        if (fencingToken <= 0) throw new IllegalArgumentException("fencingToken must be positive");
        return sql.executeTransactionally(connection -> publish(connection, group, candidate, fencingToken));
    }

    @Override
    public CompletionStage<Void> recordNodeState(
            ReplicaGroupIdentity group,
            ReplicaNodeIdentity node,
            long appliedGeneration,
            ReplicaStatus.State state,
            String detail
    ) {
        return sql.executeUpdate(
                "INSERT INTO ff_replica_node_state "
                        + "(namespace,application_id,group_id,node_id,applied_generation,status,detail,last_seen) "
                        + "VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6)) "
                        + "ON DUPLICATE KEY UPDATE applied_generation=VALUES(applied_generation),status=VALUES(status),"
                        + "detail=VALUES(detail),last_seen=CURRENT_TIMESTAMP(6)",
                group.namespace(), group.applicationId(), group.groupId(), node.nodeId(),
                appliedGeneration <= 0 ? null : appliedGeneration, state.name(), detail
        );
    }

    /** Verifies the complete version-1 table/column contract; it never applies DDL. */
    public CompletionStage<Void> validateSchema() {
        List<String> tables = REQUIRED_SCHEMA.keySet().stream().sorted().toList();
        return sql.queryForList(
                "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND TABLE_NAME IN (?,?,?,?)",
                tables.get(0), tables.get(1), tables.get(2), tables.get(3)
        ).thenAccept(rows -> {
            Map<String, Map<String, ColumnShape>> present = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String table = Objects.toString(column(row, "TABLE_NAME"), "").toLowerCase(java.util.Locale.ROOT);
                String name = Objects.toString(column(row, "COLUMN_NAME"), "").toLowerCase(java.util.Locale.ROOT);
                String dataType = Objects.toString(column(row, "DATA_TYPE"), "").toLowerCase(java.util.Locale.ROOT);
                Object maximum = column(row, "CHARACTER_MAXIMUM_LENGTH");
                Long maximumLength = maximum instanceof Number number ? number.longValue() : null;
                if (!table.isBlank() && !name.isBlank()) {
                    present.computeIfAbsent(table, ignored -> new LinkedHashMap<>())
                            .put(name, new ColumnShape(dataType, maximumLength));
                }
            }
            List<String> problems = new ArrayList<>();
            for (Map.Entry<String, Set<String>> required : REQUIRED_SCHEMA.entrySet()) {
                Map<String, ColumnShape> available = present.get(required.getKey());
                if (available == null) {
                    problems.add(required.getKey() + " (missing table)");
                    continue;
                }
                Set<String> missing = new LinkedHashSet<>(required.getValue());
                missing.removeAll(available.keySet());
                if (!missing.isEmpty()) problems.add(required.getKey() + " missing columns " + missing);
            }
            Map<String, ColumnShape> fileColumns = present.get("ff_config_file");
            if (fileColumns != null) {
                ColumnShape path = fileColumns.get("path");
                if (path != null && (!"varchar".equals(path.dataType())
                        || path.maximumLength() == null
                        || path.maximumLength() < ConfigManifestFile.MAX_PATH_LENGTH)) {
                    problems.add("ff_config_file.path must be VARCHAR("
                            + ConfigManifestFile.MAX_PATH_LENGTH + ") or wider");
                }
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "FeatureFramework replica schema v1 is not installed or incompatible: " + problems);
            }
        });
    }

    private static ConfigGeneration publish(
            Connection connection,
            ReplicaGroupIdentity group,
            ConfigGeneration candidate,
            long fencingToken
    ) throws Exception {
        try (PreparedStatement insertGroup = connection.prepareStatement(
                "INSERT IGNORE INTO ff_replica_group "
                        + "(namespace,application_id,group_id,active_generation,active_manifest_hash,highest_fencing_token,updated_at) "
                        + "VALUES (?,?,?,NULL,NULL,0,CURRENT_TIMESTAMP(6))")) {
            bindGroup(insertGroup, group);
            insertGroup.executeUpdate();
        }

        Long activeGeneration = null;
        long highestFencingToken;
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT active_generation,highest_fencing_token FROM ff_replica_group "
                        + "WHERE namespace=? AND application_id=? AND group_id=? FOR UPDATE")) {
            bindGroup(lock, group);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Replica group row disappeared during publication");
                Object active = rows.getObject("active_generation");
                if (active != null) activeGeneration = ((Number) active).longValue();
                highestFencingToken = rows.getLong("highest_fencing_token");
            }
        }
        if (fencingToken < highestFencingToken) {
            throw new IllegalStateException("Stale fencing token " + fencingToken + " < " + highestFencingToken);
        }

        long generation = activeGeneration == null ? 1L : activeGeneration + 1L;
        ConfigManifest source = candidate.manifest();
        ConfigManifest manifest = new ConfigManifest(
                source.protocolVersion(), group, generation, source.publisherNode(), source.publisherBootId(),
                fencingToken, source.applicationVersion(), source.configCompatibilityVersion(), source.createdAt(),
                source.sourceGeneration(), source.files(), source.manifestHash());
        ConfigGeneration published = new ConfigGeneration(manifest, candidate.files());

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ff_config_generation "
                        + "(namespace,application_id,group_id,generation,publisher_node,publisher_boot_id,fencing_token,"
                        + "application_version,config_compatibility_version,created_at,source_generation,manifest_hash) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int index = bindGroup(insert, group);
            insert.setLong(index++, generation);
            insert.setString(index++, manifest.publisherNode());
            insert.setString(index++, manifest.publisherBootId());
            insert.setLong(index++, fencingToken);
            insert.setString(index++, manifest.applicationVersion());
            insert.setString(index++, manifest.configCompatibilityVersion());
            insert.setTimestamp(index++, Timestamp.from(manifest.createdAt()));
            if (manifest.sourceGeneration().isPresent()) {
                insert.setLong(index++, manifest.sourceGeneration().getAsLong());
            } else {
                insert.setObject(index++, null);
            }
            insert.setString(index, manifest.manifestHash());
            insert.executeUpdate();
        }

        try (PreparedStatement insertFile = connection.prepareStatement(
                "INSERT INTO ff_config_file "
                        + "(namespace,application_id,group_id,generation,path,kind,sha256,size,raw_contents) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)")) {
            for (ConfigManifestFile file : manifest.files()) {
                int index = bindGroup(insertFile, group);
                insertFile.setLong(index++, generation);
                insertFile.setString(index++, file.path());
                insertFile.setString(index++, file.kind());
                insertFile.setString(index++, file.sha256());
                insertFile.setLong(index++, file.size());
                insertFile.setBytes(index, published.file(file.path()));
                insertFile.addBatch();
            }
            insertFile.executeBatch();
        }

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE ff_replica_group SET active_generation=?,active_manifest_hash=?,highest_fencing_token=?,"
                        + "updated_at=CURRENT_TIMESTAMP(6) WHERE namespace=? AND application_id=? AND group_id=?")) {
            update.setLong(1, generation);
            update.setString(2, manifest.manifestHash());
            update.setLong(3, Math.max(highestFencingToken, fencingToken));
            update.setString(4, group.namespace());
            update.setString(5, group.applicationId());
            update.setString(6, group.groupId());
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("Replica group activation update failed");
            }
        }
        return published;
    }

    private static Optional<ConfigGeneration> loadGeneration(
            Connection connection,
            ReplicaGroupIdentity group,
            long generation
    ) throws Exception {
        ConfigManifest header;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT publisher_node,publisher_boot_id,fencing_token,application_version,config_compatibility_version,"
                        + "created_at,source_generation,manifest_hash FROM ff_config_generation "
                        + "WHERE namespace=? AND application_id=? AND group_id=? AND generation=?")) {
            int index = bindGroup(query, group);
            query.setLong(index, generation);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return Optional.empty();
                Object source = row.getObject("source_generation");
                header = new ConfigManifest(
                        ConfigManifest.CURRENT_PROTOCOL_VERSION, group, generation,
                        row.getString("publisher_node"), row.getString("publisher_boot_id"),
                        row.getLong("fencing_token"), row.getString("application_version"),
                        row.getString("config_compatibility_version"), row.getTimestamp("created_at").toInstant(),
                        source == null ? OptionalLong.empty() : OptionalLong.of(((Number) source).longValue()),
                        List.of(), row.getString("manifest_hash"));
            }
        }

        List<ConfigManifestFile> manifestFiles = new ArrayList<>();
        Map<String, byte[]> contents = new LinkedHashMap<>();
        try (PreparedStatement queryFiles = connection.prepareStatement(
                "SELECT path,kind,sha256,size,raw_contents FROM ff_config_file "
                        + "WHERE namespace=? AND application_id=? AND group_id=? AND generation=? ORDER BY path")) {
            int index = bindGroup(queryFiles, group);
            queryFiles.setLong(index, generation);
            try (ResultSet rows = queryFiles.executeQuery()) {
                while (rows.next()) {
                    ConfigManifestFile file = new ConfigManifestFile(rows.getString("path"), rows.getString("kind"),
                            rows.getString("sha256"), rows.getLong("size"));
                    manifestFiles.add(file);
                    contents.put(file.path(), rows.getBytes("raw_contents"));
                }
            }
        }
        ConfigManifest manifest = new ConfigManifest(
                header.protocolVersion(), group, generation, header.publisherNode(), header.publisherBootId(),
                header.fencingToken(), header.applicationVersion(), header.configCompatibilityVersion(),
                header.createdAt(), header.sourceGeneration(), manifestFiles, header.manifestHash());
        return Optional.of(new ConfigGeneration(manifest, contents));
    }

    private static Object column(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static int bindGroup(PreparedStatement statement, ReplicaGroupIdentity group) throws Exception {
        statement.setString(1, group.namespace());
        statement.setString(2, group.applicationId());
        statement.setString(3, group.groupId());
        return 4;
    }

    private record ColumnShape(String dataType, Long maximumLength) { }
}
