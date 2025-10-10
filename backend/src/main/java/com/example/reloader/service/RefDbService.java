package com.example.reloader.service;

import com.example.reloader.config.RefDbProperties;
import com.example.reloader.stage.PayloadCandidate;
import com.example.reloader.stage.StageRecord;
import com.example.reloader.stage.StageStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RefDbService {
    private static final Logger log = LoggerFactory.getLogger(RefDbService.class);

    private final RefDbProperties properties;
    private final HikariDataSource dataSource;
    private final boolean isOracle;

    public RefDbService(RefDbProperties properties) {
        this.properties = properties;
        this.isOracle = properties.getHost() != null && !properties.getHost().isBlank();
        HikariConfig config = new HikariConfig();
        if (isOracle) {
            config.setJdbcUrl(properties.buildJdbcUrl());
            config.setUsername(properties.getUser());
            config.setPassword(properties.getPassword());
            config.setDriverClassName("oracle.jdbc.OracleDriver");
        } else {
            // Test environment fallback: use an embedded H2 datasource so tests don't try to contact Oracle
            config.setJdbcUrl("jdbc:h2:mem:refdb;DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
        }
        config.setMaximumPoolSize(properties.getPool().getMaxSize());
        config.setMinimumIdle(properties.getPool().getMinIdle());
        config.setPoolName("refdb-staging");
        this.dataSource = new HikariDataSource(config);
    }

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection()) {
            ensureStageTable(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to initialize staging schema", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void stagePayloads(String site, int senderId, List<PayloadCandidate> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        String table = properties.getStagingTable();
    String sql = "INSERT INTO " + table + " (id, site, sender_id, metadata_id, data_id, status, error_message, created_at, updated_at) " +
        "VALUES (" + table + "_SEQ.NEXTVAL, ?, ?, ?, ?, 'NEW', NULL, " + timestampExpr() + ", " + timestampExpr() + ")";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PayloadCandidate candidate : payloads) {
                ps.setString(1, site);
                ps.setInt(2, senderId);
                ps.setString(3, candidate.metadataId());
                ps.setString(4, candidate.dataId());
                try {
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (isDuplicate(ex)) {
                        markRetry(connection, site, senderId, candidate);
                    } else {
                        throw ex;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed staging payloads", ex);
        }
    }

    public List<StageRecord> fetchNextBatch(int limit) {
        String table = properties.getStagingTable();
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + ", created_at, updated_at " +
        "FROM " + table + " WHERE status = 'NEW' ORDER BY created_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new StageRecord(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getInt(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getString(7),
                            toInstant(rs.getTimestamp(8)),
                            toInstant(rs.getTimestamp(9))
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading batch", ex);
        }
        return records;
    }

    public void markSent(List<Long> ids) {
        updateStatus(ids, "SENT", null);
    }

    public void markFailed(long id, String message) {
        updateStatus(List.of(id), "FAILED", message);
    }

    public List<StageStatus> fetchStatuses() {
        String table = properties.getStagingTable();
        String sql = "SELECT site, sender_id, COUNT(*), " +
                "SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) " +
                "FROM " + table + " GROUP BY site, sender_id";
        List<StageStatus> statuses = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                statuses.add(new StageStatus(
                        rs.getString(1),
                        rs.getInt(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getLong(5),
                        rs.getLong(6)
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading stage status", ex);
        }
        return statuses;
    }

    public Set<String> findSitesWithPending() {
        String table = properties.getStagingTable();
        String sql = "SELECT DISTINCT site FROM " + table + " WHERE status = 'NEW'";
        Set<String> sites = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sites.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed enumerating pending sites", ex);
        }
        return sites;
    }

    public List<StageRecord> fetchNextBatchForSite(String site, int limit) {
        String table = properties.getStagingTable();
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + ", created_at, updated_at " +
        "FROM " + table + " WHERE status = 'NEW' AND site = ? ORDER BY created_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, site);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new StageRecord(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getInt(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getString(7),
                            toInstant(rs.getTimestamp(8)),
                            toInstant(rs.getTimestamp(9))
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading site batch", ex);
        }
        return records;
    }

    private void updateStatus(List<Long> ids, String status, String message) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String table = properties.getStagingTable();
    String sql = "UPDATE " + table + " SET status = ?, error_message = ?, updated_at = " + timestampExpr() + " WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                ps.setString(1, status);
                if (message == null) {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(2, truncate(message));
                }
                ps.setLong(3, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed updating status", ex);
        }
    }

    private void ensureStageTable(Connection connection) throws SQLException {
        String table = properties.getStagingTable();
        if (!tableExists(connection, table)) {
            createTable(connection, table);
        }
        if (!sequenceExists(connection, table + "_SEQ")) {
            createSequence(connection, table + "_SEQ");
        }
        if (!constraintExists(connection, table, "UK_" + table + "_SITE_PAYLOAD")) {
            addUniqueConstraint(connection, table, "UK_" + table + "_SITE_PAYLOAD");
        }
        if (!indexExists(connection, table, table + "_STATUS_IDX")) {
            addStatusIndex(connection, table, table + "_STATUS_IDX");
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        if (isOracle) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM user_tables WHERE table_name = ?")) {
                ps.setString(1, table.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            // H2: use INFORMATION_SCHEMA
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
                ps.setString(1, table.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    private boolean sequenceExists(Connection connection, String sequence) throws SQLException {
        if (isOracle) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM user_sequences WHERE sequence_name = ?")) {
                ps.setString(1, sequence.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME = ?")) {
                ps.setString(1, sequence.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    private boolean constraintExists(Connection connection, String table, String constraint) throws SQLException {
        if (isOracle) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM user_constraints WHERE table_name = ? AND constraint_name = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, constraint.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            // H2 exposes table constraints via INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = ? AND CONSTRAINT_NAME = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, constraint.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        if (isOracle) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM user_indexes WHERE table_name = ? AND index_name = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, index.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = ? AND INDEX_NAME = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, index.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    private void createTable(Connection connection, String table) throws SQLException {
        String ddl;
        if (isOracle) {
            ddl = "CREATE TABLE " + table + " (" +
                    "id NUMBER PRIMARY KEY, " +
                    "site VARCHAR2(64) NOT NULL, " +
                    "sender_id NUMBER NOT NULL, " +
                    "metadata_id VARCHAR2(128) NOT NULL, " +
                    "data_id VARCHAR2(128) NOT NULL, " +
                    "status VARCHAR2(16) DEFAULT 'NEW' NOT NULL, " +
                    "error_message VARCHAR2(4000), " +
                    "created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL" +
                    ")";
        } else {
            ddl = "CREATE TABLE " + table + " (" +
                    "id BIGINT PRIMARY KEY, " +
                    "site VARCHAR(64) NOT NULL, " +
                    "sender_id INT NOT NULL, " +
                    "metadata_id VARCHAR(128) NOT NULL, " +
                    "data_id VARCHAR(128) NOT NULL, " +
                    "status VARCHAR(16) DEFAULT 'NEW' NOT NULL, " +
                    "error_message VARCHAR(4000), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL" +
                    ")";
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private void createSequence(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isOracle) {
                statement.executeUpdate("CREATE SEQUENCE " + name + " START WITH 1 INCREMENT BY 1 NOCACHE");
            } else {
                statement.executeUpdate("CREATE SEQUENCE " + name + " START WITH 1 INCREMENT BY 1");
            }
        }
    }

    private void addUniqueConstraint(Connection connection, String table, String constraint) throws SQLException {
        String ddl = "ALTER TABLE " + table + " ADD CONSTRAINT " + constraint +
                " UNIQUE (site, sender_id, metadata_id, data_id)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private void addStatusIndex(Connection connection, String table, String index) throws SQLException {
        String ddl = "CREATE INDEX " + index + " ON " + table + " (status, site)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private void markRetry(Connection connection, String site, int senderId, PayloadCandidate candidate) {
        String table = properties.getStagingTable();
        String sql = "UPDATE " + table + " SET status = 'NEW', error_message = NULL, updated_at = SYSTIMESTAMP " +
                "WHERE site = ? AND sender_id = ? AND metadata_id = ? AND data_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, site);
            ps.setInt(2, senderId);
            ps.setString(3, candidate.metadataId());
            ps.setString(4, candidate.dataId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.warn("Failed updating duplicate payload for retry: {}", candidate, ex);
        }
    }

    private boolean isDuplicate(SQLException ex) {
        return ex.getErrorCode() == 1 ||
                (ex.getMessage() != null && ex.getMessage().toUpperCase().contains("UNIQUE"));
    }

    private Instant toInstant(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 4000 ? message.substring(0, 4000) : message;
    }

    // H2 vs Oracle SQL fragments
    private String timestampExpr() {
        return isOracle ? "SYSTIMESTAMP" : "CURRENT_TIMESTAMP";
    }

    private String coalesce(String expr, String alt) {
        // Oracle uses NVL, H2 supports COALESCE
        return isOracle ? ("NVL(" + expr + ", " + alt + ")") : ("COALESCE(" + expr + ", " + alt + ")");
    }
}
