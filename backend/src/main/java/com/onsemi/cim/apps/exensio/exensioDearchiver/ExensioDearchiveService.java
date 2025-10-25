package com.onsemi.cim.apps.exensio.exensioDearchiver;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.exensioDearchiver.config.RefDbProperties;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.LoadSessionRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.PayloadCandidate;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageResult;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageStatus;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.ReloadFilterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ExensioDearchiveService {
    private static final Logger log = LoggerFactory.getLogger(ExensioDearchiveService.class);

    private final ExternalDbConfig externalDbConfig;
    private final RefDbService refDbService;
    private final RefDbProperties refDbProperties;
    private final LoadSessionRepository loadSessionRepository;

    public ExensioDearchiveService(ExternalDbConfig externalDbConfig, RefDbService refDbService, RefDbProperties refDbProperties, LoadSessionRepository loadSessionRepository) {
        this.externalDbConfig = externalDbConfig;
        this.refDbService = refDbService;
        this.refDbProperties = refDbProperties;
        this.loadSessionRepository = loadSessionRepository;
    }

    public List<String> getSites() {
        List<String> keys = new ArrayList<>(externalDbConfig.getConfiguredKeys());
        Collections.sort(keys);
        return keys;
    }

    public String processReload(Map<String, String> params) {
        String site = params.get("site");
        if (site == null || site.isBlank()) {
            return "Site is required";
        }

        int senderId;
        try {
            senderId = Integer.parseInt(params.getOrDefault("senderId", "0"));
        } catch (NumberFormatException ex) {
            return "Invalid senderId";
        }
        if (senderId <= 0) {
            return "senderId must be greater than 0";
        }

        String startDate = emptyToNull(params.get("startDate"));
        String endDate = emptyToNull(params.get("endDate"));
        String testerType = emptyToNull(params.get("testerType"));
        String dataType = emptyToNull(params.get("dataType"));
        String location = emptyToNull(params.get("location"));
        String testPhase = emptyToNull(params.get("testPhase"));

        String environment = emptyToNull(params.get("environment"));
        String initiatedBy = "ui";
        String source = emptyToNull(params.get("source"));
        if (source == null) source = "ui";
        LoadSession session = new LoadSession(initiatedBy, site, environment, senderId, source);
        session.setStatus("CREATED");
        session = loadSessionRepository.save(session);

        String listFile = emptyToNull(params.get("listFile"));
        if (listFile != null) {
            log.info("List file provided; skipping discovery for site {} and leaving session {} ready for manual payload insert.", site, session.getId());
            return String.format("Session %d created for %s; discovery skipped due to list file.", session.getId(), site);
        }

        try {
            List<PayloadCandidate> discovered = discoverPayloads(site, startDate, endDate, testerType, dataType, location, testPhase);
            if (discovered.isEmpty()) {
                return "No payloads discovered for " + site;
            }
            StageResult result = refDbService.stagePayloads(site, senderId, discovered);
            log.info("Staged {} payloads for site {} sender {} ({} duplicates)", result.stagedCount(), site, senderId, result.duplicates().size());
            if (!result.duplicates().isEmpty()) {
                log.debug("Skipped payloads during staging: {}", result.duplicates());
            }
            return String.format("Staged %d payloads for %s. Dispatch threshold is %d per run.",
                result.stagedCount(), site, refDbProperties.getDispatch().getPerSend());
        } catch (SQLException ex) {
            log.error("Failed discovering payloads for site {}", site, ex);
            return "Error during discovery: " + ex.getMessage();
        }
    }

    public List<StageStatus> getStageStatuses() {
        return refDbService.fetchStatuses();
    }

    public List<StageStatus> getStageStatuses(String site, Integer senderId) {
        return refDbService.fetchStatusesFor(site, senderId);
    }

    /**
     * Return stage statuses but scoped to a particular user (SQL-level filter for performance).
     */
    public List<StageStatus> getStageStatusesForUser(String site, Integer senderId, String userKey) {
        return refDbService.fetchStatusesForUser(site, senderId, userKey);
    }

    private List<PayloadCandidate> discoverPayloads(String site, String startDate, String endDate, String testerType, String dataType, String location, String testPhase) throws SQLException {
        List<PayloadCandidate> results = new ArrayList<>();
        String sql = "SELECT id, id_data FROM all_metadata_view WHERE 1=1";
        List<Object> params = new ArrayList<>();
        if (startDate != null) {
            sql += " AND end_time >= ?";
            params.add(startDate);
        }
        if (endDate != null) {
            sql += " AND end_time <= ?";
            params.add(endDate);
        }
        if (testerType != null) {
            sql += " AND tester_type = ?";
            params.add(testerType);
        }
        if (dataType != null) {
            sql += " AND data_type = ?";
            params.add(dataType);
        }
        if (location != null) {
            sql += " AND location = ?";
            params.add(location);
        }
        if (testPhase != null) {
            sql += " AND test_phase = ?";
            params.add(testPhase);
        }

        try (Connection connection = externalDbConfig.getConnection(site);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String metadataId = rs.getString(1);
                    String dataId = rs.getString(2);
                    if (metadataId == null || dataId == null) {
                        continue;
                    }
                    results.add(new PayloadCandidate(metadataId, dataId));
                }
            }
        }
        return results;
    }

    public ReloadFilterOptions getReloadFilters(String site) {
        ReloadFilterOptions options = new ReloadFilterOptions();
        if (site == null || site.isBlank()) {
            return options;
        }

        String sql = "select distinct location, data_type, tester_type, data_type_ext, file_type " +
                "from dtp_simple_client_setting where enabled = 'Y' " +
                "order by location, data_type, tester_type, data_type_ext, file_type";

        try (Connection connection = externalDbConfig.getConnection(site);
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            java.util.LinkedHashSet<String> locations = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> dataTypes = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> testerTypes = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> dataTypeExt = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> fileTypes = new java.util.LinkedHashSet<>();

            while (rs.next()) {
                String loc = emptyToNull(rs.getString("location"));
                if (loc != null) locations.add(loc);
                String data = emptyToNull(rs.getString("data_type"));
                if (data != null) dataTypes.add(data);
                String tester = emptyToNull(rs.getString("tester_type"));
                if (tester != null) testerTypes.add(tester);
                String dataExt = emptyToNull(rs.getString("data_type_ext"));
                if (dataExt != null) dataTypeExt.add(dataExt);
                String fileType = emptyToNull(rs.getString("file_type"));
                if (fileType != null) fileTypes.add(fileType);
            }

            options.setLocations(new java.util.ArrayList<>(locations));
            options.setDataTypes(new java.util.ArrayList<>(dataTypes));
            options.setTesterTypes(new java.util.ArrayList<>(testerTypes));
            options.setDataTypeExt(new java.util.ArrayList<>(dataTypeExt));
            options.setFileTypes(new java.util.ArrayList<>(fileTypes));
        } catch (SQLException ex) {
            log.error("Failed loading reload filters for site {}", site, ex);
            throw new RuntimeException("Failed loading reload filters", ex);
        }

        return options;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
