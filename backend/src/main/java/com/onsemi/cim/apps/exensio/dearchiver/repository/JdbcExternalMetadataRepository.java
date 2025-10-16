package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class JdbcExternalMetadataRepository implements ExternalMetadataRepository {
    private final Logger log = LoggerFactory.getLogger(JdbcExternalMetadataRepository.class);
    private final ExternalDbConfig externalDbConfig;

    public JdbcExternalMetadataRepository(ExternalDbConfig externalDbConfig) {
        this.externalDbConfig = externalDbConfig;
    }

    // using top-level SenderCandidate DTO

    @Override
    public List<MetadataRow> findMetadata(String site, String environment, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location, int limit) {
        List<MetadataRow> rows = new ArrayList<>();
        streamMetadata(site, environment, start, end, dataType, testPhase, testerType, location, limit, rows::add);
        return rows;
    }

    @Override
    public List<MetadataRow> findMetadataPage(String site, String environment, LocalDateTime start, LocalDateTime end,
                                              String dataType, String testPhase, String testerType, String location,
                                              int offset, int limit) {
        SqlWithParams sql = buildMetadataQuery("select lot, id, id_data, end_time from all_metadata_view",
                start, end, dataType, testPhase, testerType, location);
        sql.append(" order by end_time desc");
        if (limit > 0) {
            sql.append(" offset ? rows fetch next ? rows only");
            sql.params.add(Math.max(offset, 0));
            sql.params.add(limit);
        }
        try (Connection c = externalDbConfig.getConnection(site, environment);
             PreparedStatement ps = prepareStatement(c, sql);
             ResultSet rs = ps.executeQuery()) {
            List<MetadataRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapMetadataRow(rs));
            }
            return rows;
        } catch (Exception ex) {
            log.error("Failed fetching metadata page for site {} env {}: {}", site, environment, ex.getMessage(), ex);
            throw new RuntimeException("External metadata read failed", ex);
        }
    }

    @Override
    public String describePreviewQuery(LocalDateTime start,
                                       LocalDateTime end,
                                       String dataType,
                                       String testPhase,
                                       String testerType,
                                       String location,
                                       int offset,
                                       int limit) {
        SqlWithParams sql = buildMetadataQuery("select lot, id, id_data, end_time from all_metadata_view",
                start, end, dataType, testPhase, testerType, location);
        sql.append(" order by end_time desc");
        if (limit > 0) {
            sql.append(" offset ? rows fetch next ? rows only");
            sql.params.add(Math.max(offset, 0));
            sql.params.add(limit);
        }
        return sql.format();
    }

    @Override
    public long countMetadata(String site, String environment, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location) {
        SqlWithParams sql = buildMetadataQuery("select count(1) from all_metadata_view",
                start, end, dataType, testPhase, testerType, location);
        try (Connection c = externalDbConfig.getConnection(site, environment);
             PreparedStatement ps = prepareStatement(c, sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception ex) {
            log.error("Failed counting metadata for site {} env {}: {}", site, environment, ex.getMessage(), ex);
            throw new RuntimeException("External metadata count failed", ex);
        }
    }

    @Override
    public void streamMetadata(String site, String environment, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location, int limit, java.util.function.Consumer<MetadataRow> consumer) {
        try (Connection c = externalDbConfig.getConnection(site, environment)) {
            streamMetadataWithConnection(c, start, end, dataType, testPhase, testerType, location, limit, consumer);
        } catch (Exception ex) {
            log.error("Failed streaming metadata for site {} env {}: {}", site, environment, ex.getMessage(), ex);
            throw new RuntimeException("External metadata read failed", ex);
        }
    }

    @Override
    public void streamMetadataWithConnection(Connection c, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location, int limit, java.util.function.Consumer<MetadataRow> consumer) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            SqlWithParams sql = buildMetadataQuery("select lot, id, id_data, end_time from all_metadata_view",
                    start, end, dataType, testPhase, testerType, location);
            if (limit > 0) {
                sql.append(" fetch first ").append(String.valueOf(limit)).append(" rows only");
            }
            ps = prepareStatement(c, sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                consumer.accept(mapMetadataRow(rs));
            }
        } catch (Exception ex) {
            log.error("Failed streaming metadata using provided connection: {}", ex.getMessage(), ex);
            throw new RuntimeException("External metadata read failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public java.util.List<SenderCandidate> findSendersWithConnection(Connection c, String location, String dataType, String testerType, String testPhase) {
        StringBuilder sb = new StringBuilder();
    // include the ORDER BY expressions in the select list to satisfy H2 when using DISTINCT
    sb.append("select distinct dc.id_sender, ss.name, dl.location, dd.data_type, dt.type, de.data_type_ext from dtp_dist_conf dc ");
        sb.append("left join dtp_location dl on dc.id_location = dl.id ");
        sb.append("left join dtp_data_type dd on dc.id_data_type = dd.id ");
        sb.append("left join dtp_tester_type dt on dc.id_tester_type = dt.id ");
        sb.append("left join dtp_data_type_ext de on dc.id_data_type_ext = de.id ");
        sb.append("left join dtp_sender ss on dc.id_sender = ss.id ");
        sb.append(" where 1=1 ");
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sb.append(" and dl.location = ?"); params.add(location); }
        if (dataType != null && !dataType.isBlank()) { sb.append(" and dd.data_type = ?"); params.add(dataType); }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and dt.type = ?"); params.add(testerType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and dc.id_data_type_ext IS NULL");
            } else {
                sb.append(" and de.data_type_ext = ?"); params.add(testPhase);
            }
        }
        sb.append(" order by dl.location, dd.data_type, dt.type, de.data_type_ext");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            int idx = 1;
            for (Object p : params) {
                ps.setString(idx++, p == null ? null : p.toString());
            }
            rs = ps.executeQuery();
            List<SenderCandidate> out = new ArrayList<>();
            while (rs.next()) {
                Integer id = null;
                try { id = rs.getInt("id_sender"); if (rs.wasNull()) id = null; } catch (Exception ignore) {}
                String name = null;
                try { name = rs.getString("name"); } catch (Exception ignore) {}
                if (id != null || (name != null && !name.isBlank())) {
                    out.add(new SenderCandidate(id, name));
                }
            }
            return out;
        } catch (Exception ex) {
            log.error("Failed running sender lookup: {}", ex.getMessage(), ex);
            throw new RuntimeException("Sender lookup failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public java.util.List<SenderCandidate> findAllSendersWithConnection(Connection c) {
        String sql = "select id, name from dtp_sender order by name";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            rs = ps.executeQuery();
            List<SenderCandidate> out = new ArrayList<>();
            while (rs.next()) {
                Integer id = null;
                try { id = rs.getInt("id"); if (rs.wasNull()) id = null; } catch (Exception ignore) {}
                String name = null;
                try { name = rs.getString("name"); } catch (Exception ignore) {}
                if (id != null || (name != null && !name.isBlank())) {
                    out.add(new SenderCandidate(id, name));
                }
            }
            return out;
        } catch (Exception ex) {
            log.error("Failed fetching sender list: {}", ex.getMessage(), ex);
            throw new RuntimeException("Sender list query failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public java.util.List<String> findDistinctLocationsWithConnection(Connection c, String dataType, String testerType, String testPhase) {
        // New source table: dtp_simple_client_setting
        String sql = "select distinct location from dtp_simple_client_setting where enabled = 'Y'";
        List<Object> params = new ArrayList<>();
        if (dataType != null && !dataType.isBlank()) { sql += " and data_type = ?"; params.add(dataType); }
        if (testerType != null && !testerType.isBlank()) { sql += " and tester_type = ?"; params.add(testerType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sql += " and (data_type_ext IS NULL or data_type_ext = '')";
            } else {
                sql += " and data_type_ext = ?"; params.add(testPhase);
            }
        }
        sql += " order by location, data_type, tester_type, data_type_ext, file_type";

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            int idx = 1;
            for (Object p : params) ps.setString(idx++, p == null ? null : p.toString());
            rs = ps.executeQuery();
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null && !v.isBlank()) out.add(v);
            }
            return out;
        } catch (Exception ex) {
            log.error("Failed fetching distinct locations from simple_client_setting: {}", ex.getMessage(), ex);
            throw new RuntimeException("Distinct locations query failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public java.util.List<String> findDistinctDataTypesWithConnection(Connection c, String location, String testerType, String testPhase) {
        String sql = "select distinct data_type from dtp_simple_client_setting where enabled = 'Y'";
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sql += " and location = ?"; params.add(location); }
        if (testerType != null && !testerType.isBlank()) { sql += " and tester_type = ?"; params.add(testerType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sql += " and (data_type_ext IS NULL or data_type_ext = '')";
            } else {
                sql += " and data_type_ext = ?"; params.add(testPhase);
            }
        }
        sql += " order by location, data_type, tester_type, data_type_ext, file_type";

        PreparedStatement ps = null; ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            int idx = 1; for (Object p : params) ps.setString(idx++, p == null ? null : p.toString());
            rs = ps.executeQuery(); List<String> out = new ArrayList<>(); while (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) out.add(v); } return out;
        } catch (Exception ex) { log.error("Failed fetching distinct data types from simple_client_setting: {}", ex.getMessage(), ex); throw new RuntimeException("Distinct data types query failed", ex); } finally { try { if (rs != null) rs.close(); } catch (Exception ignore) {} try { if (ps != null) ps.close(); } catch (Exception ignore) {} }
    }

    @Override
    public java.util.List<String> findDistinctTesterTypesWithConnection(Connection c, String location, String dataType, String testPhase) {
        String sql = "select distinct tester_type from dtp_simple_client_setting where enabled = 'Y'";
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sql += " and location = ?"; params.add(location); }
        if (dataType != null && !dataType.isBlank()) { sql += " and data_type = ?"; params.add(dataType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sql += " and (data_type_ext IS NULL or data_type_ext = '')";
            } else {
                sql += " and data_type_ext = ?"; params.add(testPhase);
            }
        }
        sql += " order by location, data_type, tester_type, data_type_ext, file_type";

        PreparedStatement ps = null; ResultSet rs = null;
        try { ps = c.prepareStatement(sql); int idx = 1; for (Object p : params) ps.setString(idx++, p == null ? null : p.toString()); rs = ps.executeQuery(); List<String> out = new ArrayList<>(); while (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) out.add(v); } return out; } catch (Exception ex) { log.error("Failed fetching distinct tester types from simple_client_setting: {}", ex.getMessage(), ex); throw new RuntimeException("Distinct tester types query failed", ex); } finally { try { if (rs != null) rs.close(); } catch (Exception ignore) {} try { if (ps != null) ps.close(); } catch (Exception ignore) {} }
    }

    @Override
    public java.util.List<String> findDistinctTestPhasesWithConnection(Connection c,
                                                                       String location,
                                                                       String dataType,
                                                                       String testerType,
                                                                       Integer senderId,
                                                                       String senderName) {
        java.util.LinkedHashSet<String> phases = new java.util.LinkedHashSet<>();
        if (location == null || location.isBlank() || dataType == null || dataType.isBlank() || testerType == null || testerType.isBlank()) {
            return new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("select distinct upper(trim(test_phase)) as test_phase_norm from all_metadata_view where test_phase is not null");
        List<Object> params = new ArrayList<>();
        sb.append(" and location = ?"); params.add(location);
        sb.append(" and data_type = ?"); params.add(dataType);
        sb.append(" and tester_type = ?"); params.add(testerType);
        sb.append(" order by 1");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            int idx = 1;
            for (Object p : params) {
                ps.setString(idx++, p == null ? null : p.toString());
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                String phase = null;
                try { phase = rs.getString("test_phase_norm"); } catch (Exception ignore) {}
                phases.add(phase == null ? "" : phase);
            }
            return new ArrayList<>(phases);
        } catch (Exception ex) {
            log.error("Failed fetching distinct test phases: {}", ex.getMessage(), ex);
            throw new RuntimeException("Distinct test phases query failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    private SqlWithParams buildMetadataQuery(String select, LocalDateTime start, LocalDateTime end,
                                             String dataType, String testPhase, String testerType, String location) {
        SqlWithParams result = new SqlWithParams(select + " where end_time BETWEEN ? AND ?");
        result.params.add(Timestamp.valueOf(start));
        result.params.add(Timestamp.valueOf(end));
        if (dataType != null && !dataType.isBlank()) {
            result.append(" and data_type = ?");
            result.params.add(dataType);
        }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                result.append(" and test_phase IS NULL");
            } else {
                result.append(" and UPPER(test_phase) = ?");
                result.params.add(testPhase.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (testerType != null && !testerType.isBlank()) {
            result.append(" and tester_type = ?");
            result.params.add(testerType);
        }
        if (location != null && !location.isBlank()) {
            result.append(" and location = ?");
            result.params.add(location);
        }
        if (log.isDebugEnabled()) {
            log.debug("Metadata query: {} params={} ", result.sql, result.params);
        }
        return result;
    }

    private PreparedStatement prepareStatement(Connection connection, SqlWithParams sql) throws Exception {
        PreparedStatement ps = connection.prepareStatement(sql.sql.toString());
        int idx = 1;
        for (Object param : sql.params) {
            if (param instanceof Timestamp ts) {
                ps.setTimestamp(idx++, ts);
            } else if (param instanceof Integer i) {
                ps.setInt(idx++, i);
            } else if (param instanceof Long l) {
                ps.setLong(idx++, l);
            } else {
                ps.setString(idx++, param == null ? null : param.toString());
            }
        }
        return ps;
    }

    private MetadataRow mapMetadataRow(ResultSet rs) throws Exception {
        String lot = rs.getString("lot");
        String id = rs.getString("id");
        String idData = rs.getString("id_data");
        Timestamp ts = rs.getTimestamp("end_time");
        LocalDateTime endTime = ts == null ? null : ts.toLocalDateTime();
        return new MetadataRow(lot, id, idData, endTime);
    }

    private static class SqlWithParams {
        final StringBuilder sql;
        final List<Object> params = new ArrayList<>();

        SqlWithParams(String base) {
            this.sql = new StringBuilder(base);
        }

        SqlWithParams append(String fragment) {
            this.sql.append(fragment);
            return this;
        }

        String format() {
            if (params.isEmpty()) {
                return sql.toString();
            }
            return sql + " /* params=" + params + " */";
        }
    }
}
