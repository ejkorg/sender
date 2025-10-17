package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.RefDbProperties;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.DuplicatePayload;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.PayloadCandidate;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageRecord;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageResult;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageStatus;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageUserStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RefDbService {
    private static final Logger log = LoggerFactory.getLogger(RefDbService.class);
    private static final String DEFAULT_USER = "system";
    private static final String UNKNOWN_USER = "unknown";
    private static final int USER_MAX_LENGTH = 120;

    private final RefDbProperties properties;
    private final HikariDataSource dataSource;
    private final boolean isOracle;
    @Value("${refdb.auth-bootstrap-enabled:false}")
    private boolean authBootstrapEnabled;

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
            if (authBootstrapEnabled) {
                ensureAuthTables(connection);
                bootstrapAdmins(connection);
            } else {
                log.info("RefDB auth bootstrap is disabled (refdb.auth-bootstrap-enabled=false). Skipping APP_* schema/seed.");
            }
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

    public StageResult stagePayloads(String site, int senderId, List<PayloadCandidate> payloads) {
        return stagePayloads(site, senderId, DEFAULT_USER, payloads, true);
    }

    public StageResult stagePayloads(String site,
                                     int senderId,
                                     String requestedBy,
                                     List<PayloadCandidate> payloads,
                                     boolean forceDuplicates) {
        if (payloads == null || payloads.isEmpty()) {
            return StageResult.empty();
        }
        String normalizedUser = normalizeUser(requestedBy);
        String table = properties.getStagingTable();
        String idExpr = nextIdExpr(table);
        String sql = "INSERT INTO " + table + " (id, site, sender_id, metadata_id, data_id, status, error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at) " +
                "VALUES (" + idExpr + ", ?, ?, ?, ?, 'NEW', NULL, " + timestampExpr() + ", " + timestampExpr() + ", NULL, ?, ?, " + timestampExpr() + ")";
        int inserted = 0;
        List<DuplicatePayload> duplicates = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (PayloadCandidate candidate : payloads) {
                ps.setString(1, site);
                ps.setInt(2, senderId);
                ps.setString(3, candidate.metadataId());
                ps.setString(4, candidate.dataId());
                ps.setString(5, normalizedUser);
                ps.setString(6, normalizedUser);
                try {
                    ps.executeUpdate();
                    inserted++;
                } catch (SQLException ex) {
                    if (isDuplicate(ex)) {
                        ExistingPayload existing = loadExistingPayload(connection, table, site, senderId, candidate);
                        boolean sameUser = false;
                        if (existing != null) {
                            String effectiveUser = normalizeUser(existing.lastRequestedBy() != null ? existing.lastRequestedBy() : existing.stagedBy());
                            sameUser = effectiveUser.equalsIgnoreCase(normalizedUser);
                        }
                        boolean allowResubmit = forceDuplicates || sameUser;
                        if (existing == null) {
                            allowResubmit = true; // fallback to original behavior if metadata missing
                        }
                        if (allowResubmit && existing != null) {
                            markRetry(connection, table, site, senderId, candidate, normalizedUser);
                        }
                        duplicates.add(toDuplicatePayload(candidate, existing, !allowResubmit));
                    } else {
                        throw ex;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed staging payloads", ex);
        }
        return new StageResult(inserted, duplicates);
    }

    public List<StageRecord> fetchNextBatch(int limit) {
        String table = properties.getStagingTable();
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + " AS error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at " +
        "FROM " + table + " WHERE status = 'NEW' ORDER BY created_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading batch", ex);
        }
        return records;
    }

    public void markEnqueued(List<Long> ids) {
        updateStatus(ids, "ENQUEUED", null);
    }

    public void markFailed(long id, String message) {
        updateStatus(List.of(id), "FAILED", message);
    }

    public void markCompleted(List<Long> ids) {
        markCompleted(ids, Instant.now());
    }

    public void markCompleted(List<Long> ids, Instant processedAt) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Instant effective = processedAt != null ? processedAt : Instant.now();
        Timestamp processedTs = Timestamp.from(effective);
        String table = properties.getStagingTable();
        String sql = "UPDATE " + table + " SET status = ?, error_message = NULL, processed_at = ?, updated_at = " + timestampExpr() + " WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Long id : ids) {
                ps.setString(1, "DONE");
                ps.setTimestamp(2, processedTs);
                ps.setLong(3, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed marking records complete", ex);
        }
    }

    public List<StageStatus> fetchStatuses() {
        String table = properties.getStagingTable();
        List<StageStatus> statuses = new ArrayList<>();
        String sql = "SELECT site, sender_id, COUNT(*), " +
                "SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'ENQUEUED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) " +
                "FROM " + table + " GROUP BY site, sender_id";
        try (Connection connection = dataSource.getConnection()) {
            Map<StageStatusKey, List<StageUserStatus>> userBreakdown = fetchUserBreakdown(connection, table, null, null);
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String site = rs.getString(1);
                    int senderId = rs.getInt(2);
                    StageStatusKey key = new StageStatusKey(site, senderId);
                    statuses.add(new StageStatus(
                            site,
                            senderId,
                            rs.getLong(3),
                            rs.getLong(4),
                            rs.getLong(5),
                            rs.getLong(6),
                            rs.getLong(7),
                            userBreakdown.getOrDefault(key, List.of())
                    ));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading stage status", ex);
        }
        return statuses;
    }

    public List<StageStatus> fetchStatusesFor(String site, Integer senderId) {
        String table = properties.getStagingTable();
        String where = " WHERE 1=1" + (site != null ? " AND site = ?" : "") + (senderId != null ? " AND sender_id = ?" : "");
        String sql = "SELECT site, sender_id, COUNT(*), " +
                "SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'ENQUEUED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) " +
                "FROM " + table + where + " GROUP BY site, sender_id";
        List<StageStatus> statuses = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            Map<StageStatusKey, List<StageUserStatus>> userBreakdown = fetchUserBreakdown(connection, table, site, senderId);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                if (site != null) ps.setString(i++, site);
                if (senderId != null) ps.setInt(i++, senderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rowSite = rs.getString(1);
                        int rowSender = rs.getInt(2);
                        StageStatusKey key = new StageStatusKey(rowSite, rowSender);
                        statuses.add(new StageStatus(
                                rowSite,
                                rowSender,
                                rs.getLong(3),
                                rs.getLong(4),
                                rs.getLong(5),
                                rs.getLong(6),
                                rs.getLong(7),
                                userBreakdown.getOrDefault(key, List.of())
                        ));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading stage status (filtered)", ex);
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
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + " AS error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at " +
        "FROM " + table + " WHERE status = 'NEW' AND site = ? ORDER BY created_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, site);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading site batch", ex);
        }
        return records;
    }

    public List<StageRecord> fetchNextBatchForSender(String site, int senderId, int limit) {
        String table = properties.getStagingTable();
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + " AS error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at " +
        "FROM " + table + " WHERE status = 'NEW' AND site = ? AND sender_id = ? ORDER BY created_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, site);
            ps.setInt(2, senderId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading sender batch", ex);
        }
        return records;
    }

    public List<StageRecord> findEnqueuedWithoutProcessed(int limit) {
        if (limit <= 0) {
            limit = 200;
        }
        String table = properties.getStagingTable();
    String sql = "SELECT id, site, sender_id, metadata_id, data_id, status, " + coalesce("error_message", "''") + " AS error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at " +
        "FROM " + table + " WHERE status = 'ENQUEUED' AND processed_at IS NULL ORDER BY updated_at FETCH FIRST ? ROWS ONLY";
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading enqueued records", ex);
        }
        return records;
    }

    public List<StageRecord> listRecords(String site, Integer senderId, String status, int limit) {
        return listRecords(site, senderId, status, 0, limit);
    }

    public List<StageRecord> listRecords(String site, Integer senderId, String status, int offset, int limit) {
        String table = properties.getStagingTable();
    StringBuilder sb = new StringBuilder("SELECT id, site, sender_id, metadata_id, data_id, status, ")
        .append(coalesce("error_message", "''"))
        .append(" AS error_message, created_at, updated_at, processed_at, staged_by, last_requested_by, last_requested_at FROM ")
                .append(table)
                .append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (site != null && !site.isBlank()) {
            sb.append(" AND site = ?");
            params.add(site);
        }
        if (senderId != null) {
            sb.append(" AND sender_id = ?");
            params.add(senderId);
        }
        if (status != null && !status.isBlank()) {
            sb.append(" AND status = ?");
            params.add(status);
        }
        sb.append(" ORDER BY updated_at DESC");
        int effectiveOffset = Math.max(offset, 0);
        if (limit > 0) {
            sb.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(effectiveOffset);
            params.add(limit);
        }
        List<StageRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object param : params) {
                if (param instanceof Integer i) ps.setInt(idx++, i);
                else if (param instanceof Long l) ps.setLong(idx++, l);
                else ps.setString(idx++, param == null ? null : param.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed loading staged records", ex);
        }
        return records;
    }

    public List<StageRecord> listRecordsByStatus(String status, int limit) {
        return listRecords(null, null, status, limit);
    }

    public long countRecords(String site, Integer senderId, String status) {
        String table = properties.getStagingTable();
        StringBuilder sb = new StringBuilder("SELECT COUNT(1) FROM ").append(table).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (site != null && !site.isBlank()) {
            sb.append(" AND site = ?");
            params.add(site);
        }
        if (senderId != null) {
            sb.append(" AND sender_id = ?");
            params.add(senderId);
        }
        if (status != null && !status.isBlank()) {
            sb.append(" AND status = ?");
            params.add(status);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object param : params) {
                if (param instanceof Integer i) ps.setInt(idx++, i);
                else if (param instanceof Long l) ps.setLong(idx++, l);
                else ps.setString(idx++, param == null ? null : param.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed counting staged records", ex);
        }
        return 0L;
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

    private StageRecord mapRecord(ResultSet rs) throws SQLException {
        return new StageRecord(
                rs.getLong("id"),
                rs.getString("site"),
                rs.getInt("sender_id"),
                rs.getString("metadata_id"),
                rs.getString("data_id"),
                rs.getString("status"),
                rs.getString("error_message"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")),
    toInstant(rs.getTimestamp("processed_at")),
    rs.getString("staged_by"),
    rs.getString("last_requested_by"),
    toInstant(rs.getTimestamp("last_requested_at"))
        );
    }

    private void ensureStageTable(Connection connection) throws SQLException {
        String table = properties.getStagingTable();
        if (!tableExists(connection, table)) {
            createTable(connection, table);
        }
        ensureProcessedAtColumn(connection, table);
        ensureUserColumns(connection, table);
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

    // --- Authorization schema (local app users/roles) ---
    private void ensureAuthTables(Connection connection) throws SQLException {
        // USERS(username PK), ROLES(name PK), USER_ROLES(username, role_name)
        if (!tableExists(connection, "APP_USERS")) {
            String ddl = isOracle
                    ? "CREATE TABLE APP_USERS (username VARCHAR2(128) PRIMARY KEY, active NUMBER(1) DEFAULT 1 NOT NULL)"
                    : "CREATE TABLE APP_USERS (username VARCHAR(128) PRIMARY KEY, active BOOLEAN DEFAULT TRUE NOT NULL)";
            try (Statement st = connection.createStatement()) { st.executeUpdate(ddl); }
        }
        if (!tableExists(connection, "APP_ROLES")) {
            String ddl = isOracle
                    ? "CREATE TABLE APP_ROLES (name VARCHAR2(64) PRIMARY KEY)"
                    : "CREATE TABLE APP_ROLES (name VARCHAR(64) PRIMARY KEY)";
            try (Statement st = connection.createStatement()) { st.executeUpdate(ddl); }
        }
        if (!tableExists(connection, "APP_USER_ROLES")) {
            String ddl = isOracle
                    ? "CREATE TABLE APP_USER_ROLES (username VARCHAR2(128) NOT NULL, role_name VARCHAR2(64) NOT NULL, CONSTRAINT PK_APP_USER_ROLES PRIMARY KEY (username, role_name))"
                    : "CREATE TABLE APP_USER_ROLES (username VARCHAR(128) NOT NULL, role_name VARCHAR(64) NOT NULL, CONSTRAINT PK_APP_USER_ROLES PRIMARY KEY (username, role_name))";
            try (Statement st = connection.createStatement()) { st.executeUpdate(ddl); }
        }
        // Ensure ROLE_USER and ROLE_ADMIN exist
        upsertRole(connection, "ROLE_USER");
        upsertRole(connection, "ROLE_ADMIN");
    }

    private void upsertRole(Connection connection, String roleName) throws SQLException {
        String sqlCheck = "SELECT COUNT(1) FROM APP_ROLES WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sqlCheck)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = connection.prepareStatement("INSERT INTO APP_ROLES(name) VALUES (?)")) {
                        ins.setString(1, roleName);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    private void bootstrapAdmins(Connection connection) throws SQLException {
        String seed = properties.getBootstrapAdmins();
        if (seed == null || seed.isBlank()) {
            return;
        }
        String[] users = seed.split(",");
        for (String u : users) {
            String username = normalizeUser(u);
            if (username.isBlank()) continue;
            ensureUser(connection, username);
            ensureUserRole(connection, username, "ROLE_USER");
            ensureUserRole(connection, username, "ROLE_ADMIN");
        }
    }

    private void ensureUser(Connection connection, String username) throws SQLException {
        String check = "SELECT COUNT(1) FROM APP_USERS WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(check)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = connection.prepareStatement("INSERT INTO APP_USERS(username, active) VALUES (?, ?)")) {
                        psCloseableSet(ins, 1, username);
                        if (isOracle) ins.setInt(2, 1); else ins.setBoolean(2, true);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    private void ensureUserRole(Connection connection, String username, String role) throws SQLException {
        String check = "SELECT COUNT(1) FROM APP_USER_ROLES WHERE username = ? AND role_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(check)) {
            ps.setString(1, username);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement ins = connection.prepareStatement("INSERT INTO APP_USER_ROLES(username, role_name) VALUES (?, ?)")) {
                        ins.setString(1, username);
                        ins.setString(2, role);
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    private void psCloseableSet(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }

    public Set<String> getUserAuthorities(String username) {
        Set<String> roles = new HashSet<>();
        if (username == null || username.isBlank()) {
            return roles;
        }
        try (Connection connection = dataSource.getConnection()) {
            // Auto-provision user on first sight
            ensureUser(connection, username);
            // Every user implicitly has ROLE_USER
            roles.add("ROLE_USER");
            String sql = "SELECT role_name FROM APP_USER_ROLES WHERE username = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        roles.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed loading authorities for {}: {}", username, ex.getMessage());
        }
        return roles;
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
            "updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, " +
            "processed_at TIMESTAMP, " +
            "staged_by VARCHAR2(128), " +
            "last_requested_by VARCHAR2(128), " +
            "last_requested_at TIMESTAMP" +
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
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, " +
            "processed_at TIMESTAMP, " +
            "staged_by VARCHAR(128), " +
            "last_requested_by VARCHAR(128), " +
            "last_requested_at TIMESTAMP" +
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

    private void ensureProcessedAtColumn(Connection connection, String table) throws SQLException {
        if (!columnExists(connection, table, "PROCESSED_AT")) {
            addProcessedAtColumn(connection, table);
        }
    }

    private void ensureUserColumns(Connection connection, String table) throws SQLException {
        boolean stagedByAdded = ensureColumn(connection, table, "STAGED_BY", isOracle
                ? "ALTER TABLE " + table + " ADD (staged_by VARCHAR2(128))"
                : "ALTER TABLE " + table + " ADD (staged_by VARCHAR(128))");
        boolean lastRequestedByAdded = ensureColumn(connection, table, "LAST_REQUESTED_BY", isOracle
                ? "ALTER TABLE " + table + " ADD (last_requested_by VARCHAR2(128))"
                : "ALTER TABLE " + table + " ADD (last_requested_by VARCHAR(128))");
        boolean lastRequestedAtAdded = ensureColumn(connection, table, "LAST_REQUESTED_AT", "ALTER TABLE " + table + " ADD (last_requested_at TIMESTAMP)");

        if (stagedByAdded || lastRequestedByAdded || lastRequestedAtAdded) {
            log.info("User metadata columns ensured for {}", table);
        }

        backfillUserColumns(connection, table);
    }

    private boolean ensureColumn(Connection connection, String table, String column, String ddl) throws SQLException {
        if (columnExists(connection, table, column)) {
            return false;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
        return true;
    }

    private void backfillUserColumns(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE " + table + " SET staged_by = '" + UNKNOWN_USER + "' WHERE staged_by IS NULL");
            statement.executeUpdate("UPDATE " + table + " SET last_requested_by = COALESCE(last_requested_by, staged_by) WHERE last_requested_by IS NULL");
            String timestampFallback = isOracle ? "COALESCE(last_requested_at, updated_at, created_at, SYSTIMESTAMP)"
                    : "COALESCE(last_requested_at, updated_at, created_at, CURRENT_TIMESTAMP)";
            statement.executeUpdate("UPDATE " + table + " SET last_requested_at = " + timestampFallback + " WHERE last_requested_at IS NULL");
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        if (isOracle) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM user_tab_cols WHERE table_name = ? AND column_name = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, column.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                ps.setString(1, table.toUpperCase());
                ps.setString(2, column.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    private void addProcessedAtColumn(Connection connection, String table) throws SQLException {
        String ddl = "ALTER TABLE " + table + " ADD (processed_at TIMESTAMP)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private Map<StageStatusKey, List<StageUserStatus>> fetchUserBreakdown(Connection connection,
                                                                          String table,
                                                                          String site,
                                                                          Integer senderId) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT site, sender_id, COALESCE(last_requested_by, staged_by) AS user_key, COUNT(*), ")
                .append("SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), ")
                .append("SUM(CASE WHEN status = 'ENQUEUED' THEN 1 ELSE 0 END), ")
                .append("SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), ")
                .append("SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END), ")
                .append("MAX(last_requested_at) FROM ")
                .append(table)
                .append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (site != null) {
            sb.append(" AND site = ?");
            params.add(site);
        }
        if (senderId != null) {
            sb.append(" AND sender_id = ?");
            params.add(senderId);
        }
        sb.append(" GROUP BY site, sender_id, COALESCE(last_requested_by, staged_by)");

        Map<StageStatusKey, List<StageUserStatus>> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Object param : params) {
                if (param instanceof Integer i) {
                    ps.setInt(idx++, i);
                } else {
                    ps.setString(idx++, param == null ? null : param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rowSite = rs.getString(1);
                    int rowSender = rs.getInt(2);
                    String rawUser = rs.getString(3);
                    long total = rs.getLong(4);
                    long ready = rs.getLong(5);
                    long enqueued = rs.getLong(6);
                    long failed = rs.getLong(7);
                    long completed = rs.getLong(8);
                    Instant lastRequestedAt = toInstant(rs.getTimestamp(9));
                    StageUserStatus userStatus = new StageUserStatus(displayUser(rawUser), total, ready, enqueued, failed, completed, lastRequestedAt);
                    StageStatusKey key = new StageStatusKey(rowSite, rowSender);
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(userStatus);
                }
            }
        }

        for (List<StageUserStatus> list : result.values()) {
            list.sort((a, b) -> {
                long backlogA = a.ready() + a.enqueued() + a.failed();
                long backlogB = b.ready() + b.enqueued() + b.failed();
                if (backlogA != backlogB) {
                    return Long.compare(backlogB, backlogA);
                }
                if (a.total() != b.total()) {
                    return Long.compare(b.total(), a.total());
                }
                String ua = a.username() == null ? "" : a.username();
                String ub = b.username() == null ? "" : b.username();
                return ua.compareToIgnoreCase(ub);
            });
        }

        return result;
    }

    private void markRetry(Connection connection,
                           String table,
                           String site,
                           int senderId,
                           PayloadCandidate candidate,
                           String requestedBy) {
        String sql = "UPDATE " + table + " SET status = 'NEW', error_message = NULL, processed_at = NULL, updated_at = " + timestampExpr() + ", " +
                "last_requested_by = ?, last_requested_at = " + timestampExpr() + " WHERE site = ? AND sender_id = ? AND metadata_id = ? AND data_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestedBy);
            ps.setString(2, site);
            ps.setInt(3, senderId);
            ps.setString(4, candidate.metadataId());
            ps.setString(5, candidate.dataId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.warn("Failed updating duplicate payload for retry: {}", candidate, ex);
        }
    }

    private ExistingPayload loadExistingPayload(Connection connection,
                                                String table,
                                                String site,
                                                int senderId,
                                                PayloadCandidate candidate) {
        String sql = "SELECT status, processed_at, created_at, staged_by, last_requested_by, last_requested_at FROM " + table +
                " WHERE site = ? AND sender_id = ? AND metadata_id = ? AND data_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, site);
            ps.setInt(2, senderId);
            ps.setString(3, candidate.metadataId());
            ps.setString(4, candidate.dataId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ExistingPayload(
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("processed_at")),
                            toInstant(rs.getTimestamp("created_at")),
                            rs.getString("staged_by"),
                            rs.getString("last_requested_by"),
                            toInstant(rs.getTimestamp("last_requested_at"))
                    );
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed loading existing payload for duplicate {}: {}", candidate, ex.getMessage());
        }
        return null;
    }

    private DuplicatePayload toDuplicatePayload(PayloadCandidate candidate, ExistingPayload existing, boolean requiresConfirmation) {
        if (existing == null) {
            return new DuplicatePayload(
                    candidate.metadataId(),
                    candidate.dataId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    requiresConfirmation
            );
        }
        String stagedBy = displayUser(existing.stagedBy());
        String lastRequestedBy = displayUser(existing.lastRequestedBy() != null ? existing.lastRequestedBy() : existing.stagedBy());
        return new DuplicatePayload(
                candidate.metadataId(),
                candidate.dataId(),
                existing.status(),
                existing.processedAt(),
                stagedBy,
                existing.createdAt(),
                lastRequestedBy,
                existing.lastRequestedAt(),
                requiresConfirmation
        );
    }

    private String normalizeUser(String value) {
        if (value == null) {
            return DEFAULT_USER;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_USER;
        }
        if (trimmed.length() > USER_MAX_LENGTH) {
            return trimmed.substring(0, USER_MAX_LENGTH);
        }
        return trimmed;
    }

    private String displayUser(String value) {
        if (value == null) {
            return UNKNOWN_USER;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? UNKNOWN_USER : trimmed;
    }

    private record ExistingPayload(String status,
                                   Instant processedAt,
                                   Instant createdAt,
                                   String stagedBy,
                                   String lastRequestedBy,
                                   Instant lastRequestedAt) {}

    private record StageStatusKey(String site, int senderId) {}

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
    private String nextIdExpr(String table) {
        String sequence = table + "_SEQ";
        return isOracle ? sequence + ".NEXTVAL" : "NEXT VALUE FOR " + sequence;
    }

    private String timestampExpr() {
        return isOracle ? "SYSTIMESTAMP" : "CURRENT_TIMESTAMP";
    }

    private String coalesce(String expr, String alt) {
        // Oracle uses NVL, H2 supports COALESCE
        return isOracle ? ("NVL(" + expr + ", " + alt + ")") : ("COALESCE(" + expr + ", " + alt + ")");
    }
}
