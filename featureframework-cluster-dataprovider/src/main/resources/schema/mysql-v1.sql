-- FeatureFramework replica control-plane schema v1.
-- Apply explicitly before enabling replicated mode. The runtime validates this schema but never creates it.

CREATE TABLE IF NOT EXISTS ff_replica_group (
    namespace VARCHAR(128) NOT NULL,
    application_id VARCHAR(128) NOT NULL,
    group_id VARCHAR(128) NOT NULL,
    active_generation BIGINT UNSIGNED NULL,
    active_manifest_hash CHAR(64) NULL,
    highest_fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (namespace, application_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ff_config_generation (
    namespace VARCHAR(128) NOT NULL,
    application_id VARCHAR(128) NOT NULL,
    group_id VARCHAR(128) NOT NULL,
    generation BIGINT UNSIGNED NOT NULL,
    publisher_node VARCHAR(128) NOT NULL,
    publisher_boot_id VARCHAR(64) NOT NULL,
    fencing_token BIGINT UNSIGNED NOT NULL,
    application_version VARCHAR(128) NOT NULL,
    config_compatibility_version VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    source_generation BIGINT UNSIGNED NULL,
    manifest_hash CHAR(64) NOT NULL,
    PRIMARY KEY (namespace, application_id, group_id, generation),
    CONSTRAINT fk_ff_generation_group FOREIGN KEY (namespace, application_id, group_id)
        REFERENCES ff_replica_group(namespace, application_id, group_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ff_config_file (
    namespace VARCHAR(128) NOT NULL,
    application_id VARCHAR(128) NOT NULL,
    group_id VARCHAR(128) NOT NULL,
    generation BIGINT UNSIGNED NOT NULL,
    path VARCHAR(512) NOT NULL,
    kind VARCHAR(64) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size BIGINT UNSIGNED NOT NULL,
    raw_contents LONGBLOB NOT NULL,
    PRIMARY KEY (namespace, application_id, group_id, generation, path),
    CONSTRAINT fk_ff_file_generation FOREIGN KEY (namespace, application_id, group_id, generation)
        REFERENCES ff_config_generation(namespace, application_id, group_id, generation)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ff_replica_node_state (
    namespace VARCHAR(128) NOT NULL,
    application_id VARCHAR(128) NOT NULL,
    group_id VARCHAR(128) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    applied_generation BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL,
    detail VARCHAR(512) NULL,
    last_seen TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (namespace, application_id, group_id, node_id),
    CONSTRAINT fk_ff_node_group FOREIGN KEY (namespace, application_id, group_id)
        REFERENCES ff_replica_group(namespace, application_id, group_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
