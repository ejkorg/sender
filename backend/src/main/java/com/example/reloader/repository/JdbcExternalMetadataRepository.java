package com.example.reloader.repository;

import com.example.reloader.config.ExternalDbConfig;
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
import java.util.Objects;

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
    public void streamMetadata(String site, String environment, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location, int limit, java.util.function.Consumer<MetadataRow> consumer) {
        StringBuilder sb = new StringBuilder("select lot, id, id_data, end_time from all_metadata_view where end_time BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(start));
        params.add(Timestamp.valueOf(end));
        if (dataType != null && !dataType.isBlank()) { sb.append(" and data_type = ?"); params.add(dataType); }
        if (testPhase != null) {
            // Treat empty string as a request to match NULL test_phase (caller wants entries with no test phase)
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and test_phase IS NULL");
            } else {
                sb.append(" and test_phase = ?"); params.add(testPhase);
            }
        }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and tester_type = ?"); params.add(testerType); }
        if (location != null && !location.isBlank()) { sb.append(" and location = ?"); params.add(location); }
        if (limit > 0) sb.append(" fetch first ").append(limit).append(" rows only");

        String sql = sb.toString();
        try (Connection c = externalDbConfig.getConnection(site, environment)) {
            streamMetadataWithConnection(c, start, end, dataType, testPhase, testerType, location, limit, consumer);
        } catch (Exception ex) {
            log.error("Failed streaming metadata for site {} env {}: {}", site, environment, ex.getMessage(), ex);
            throw new RuntimeException("External metadata read failed", ex);
        }
    }

    @Override
    public void streamMetadataWithConnection(Connection c, LocalDateTime start, LocalDateTime end, String dataType, String testPhase, String testerType, String location, int limit, java.util.function.Consumer<MetadataRow> consumer) {
        StringBuilder sb = new StringBuilder("select lot, id, id_data, end_time from all_metadata_view where end_time BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(start));
        params.add(Timestamp.valueOf(end));
        if (dataType != null && !dataType.isBlank()) { sb.append(" and data_type = ?"); params.add(dataType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and test_phase IS NULL");
            } else {
                sb.append(" and test_phase = ?"); params.add(testPhase);
            }
        }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and tester_type = ?"); params.add(testerType); }
        if (location != null && !location.isBlank()) { sb.append(" and location = ?"); params.add(location); }
        if (limit > 0) sb.append(" fetch first ").append(limit).append(" rows only");

        String sql = sb.toString();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sql);
            int idx = 1;
            for (Object p : params) {
                if (p instanceof Timestamp) ps.setTimestamp(idx++, (Timestamp) p);
                else ps.setString(idx++, p == null ? null : p.toString());
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                String lot = rs.getString("lot");
                String id = rs.getString("id");
                String idData = rs.getString("id_data");
                Timestamp ts = rs.getTimestamp("end_time");
                LocalDateTime endTime = ts == null ? null : ts.toLocalDateTime();
                consumer.accept(new MetadataRow(lot, id, idData, endTime));
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
    public java.util.List<String> findDistinctLocationsWithConnection(Connection c, String dataType, String testerType, String testPhase) {
        StringBuilder sb = new StringBuilder();
        sb.append("select distinct dl.location from dtp_dist_conf dc ");
        sb.append("left join dtp_location dl on dc.id_location = dl.id ");
        sb.append("left join dtp_data_type dd on dc.id_data_type = dd.id ");
        sb.append("left join dtp_tester_type dt on dc.id_tester_type = dt.id ");
        sb.append(" where 1=1 ");
        List<Object> params = new ArrayList<>();
        if (dataType != null && !dataType.isBlank()) { sb.append(" and dd.data_type = ?"); params.add(dataType); }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and dt.type = ?"); params.add(testerType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and dc.id_data_type_ext IS NULL");
            } else {
                sb.append(" and dc.id_data_type_ext = (select id from dtp_data_type_ext where data_type_ext = ?)"); params.add(testPhase);
            }
        }
        sb.append(" order by dl.location");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
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
            log.error("Failed fetching distinct locations: {}", ex.getMessage(), ex);
            throw new RuntimeException("Distinct locations query failed", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignore) {}
            try { if (ps != null) ps.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public java.util.List<String> findDistinctDataTypesWithConnection(Connection c, String location, String testerType, String testPhase) {
        StringBuilder sb = new StringBuilder();
        sb.append("select distinct dd.data_type from dtp_dist_conf dc ");
        sb.append("left join dtp_data_type dd on dc.id_data_type = dd.id ");
        sb.append("left join dtp_location dl on dc.id_location = dl.id ");
        sb.append("left join dtp_tester_type dt on dc.id_tester_type = dt.id ");
        sb.append(" where 1=1 ");
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sb.append(" and dl.location = ?"); params.add(location); }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and dt.type = ?"); params.add(testerType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and dc.id_data_type_ext IS NULL");
            } else {
                sb.append(" and dc.id_data_type_ext = (select id from dtp_data_type_ext where data_type_ext = ?)"); params.add(testPhase);
            }
        }
        sb.append(" order by dd.data_type");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            int idx = 1; for (Object p : params) ps.setString(idx++, p == null ? null : p.toString());
            rs = ps.executeQuery();
            List<String> out = new ArrayList<>(); while (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) out.add(v); } return out;
        } catch (Exception ex) { log.error("Failed fetching distinct data types: {}", ex.getMessage(), ex); throw new RuntimeException("Distinct data types query failed", ex); } finally { try { if (rs != null) rs.close(); } catch (Exception ignore) {} try { if (ps != null) ps.close(); } catch (Exception ignore) {} }
    }

    @Override
    public java.util.List<String> findDistinctTesterTypesWithConnection(Connection c, String location, String dataType, String testPhase) {
        StringBuilder sb = new StringBuilder();
        sb.append("select distinct dt.type from dtp_dist_conf dc ");
        sb.append("left join dtp_tester_type dt on dc.id_tester_type = dt.id ");
        sb.append("left join dtp_location dl on dc.id_location = dl.id ");
        sb.append("left join dtp_data_type dd on dc.id_data_type = dd.id ");
        sb.append(" where 1=1 ");
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sb.append(" and dl.location = ?"); params.add(location); }
        if (dataType != null && !dataType.isBlank()) { sb.append(" and dd.data_type = ?"); params.add(dataType); }
        if (testPhase != null) {
            if (testPhase.isBlank() || "NULL".equalsIgnoreCase(testPhase) || "NONE".equalsIgnoreCase(testPhase)) {
                sb.append(" and dc.id_data_type_ext IS NULL");
            } else {
                sb.append(" and dc.id_data_type_ext = (select id from dtp_data_type_ext where data_type_ext = ?)"); params.add(testPhase);
            }
        }
        sb.append(" order by dt.type");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString()); int idx = 1; for (Object p : params) ps.setString(idx++, p == null ? null : p.toString()); rs = ps.executeQuery(); List<String> out = new ArrayList<>(); while (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) out.add(v); } return out;
        } catch (Exception ex) { log.error("Failed fetching distinct tester types: {}", ex.getMessage(), ex); throw new RuntimeException("Distinct tester types query failed", ex); } finally { try { if (rs != null) rs.close(); } catch (Exception ignore) {} try { if (ps != null) ps.close(); } catch (Exception ignore) {} }
    }

    @Override
    public java.util.List<String> findDistinctTestPhasesWithConnection(Connection c, String location, String dataType, String testerType) {
        StringBuilder sb = new StringBuilder();
        sb.append("select distinct de.data_type_ext from dtp_dist_conf dc ");
        sb.append("left join dtp_data_type_ext de on dc.id_data_type_ext = de.id ");
        sb.append("left join dtp_location dl on dc.id_location = dl.id ");
        sb.append("left join dtp_data_type dd on dc.id_data_type = dd.id ");
        sb.append("left join dtp_tester_type dt on dc.id_tester_type = dt.id ");
        sb.append(" where 1=1 ");
        List<Object> params = new ArrayList<>();
        if (location != null && !location.isBlank()) { sb.append(" and dl.location = ?"); params.add(location); }
        if (dataType != null && !dataType.isBlank()) { sb.append(" and dd.data_type = ?"); params.add(dataType); }
        if (testerType != null && !testerType.isBlank()) { sb.append(" and dt.type = ?"); params.add(testerType); }
        sb.append(" order by de.data_type_ext");

        PreparedStatement ps = null; ResultSet rs = null;
        try { ps = c.prepareStatement(sb.toString()); int idx = 1; for (Object p : params) ps.setString(idx++, p == null ? null : p.toString()); rs = ps.executeQuery(); List<String> out = new ArrayList<>(); while (rs.next()) { String v = rs.getString(1); if (v != null) out.add(v); } return out; } catch (Exception ex) { log.error("Failed fetching distinct test phases: {}", ex.getMessage(), ex); throw new RuntimeException("Distinct test phases query failed", ex); } finally { try { if (rs != null) rs.close(); } catch (Exception ignore) {} try { if (ps != null) ps.close(); } catch (Exception ignore) {} }
    }
}
