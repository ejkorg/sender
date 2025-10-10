package com.example.reloader;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.config.RefDbProperties;
import com.example.reloader.service.RefDbService;
import com.example.reloader.stage.PayloadCandidate;
import com.example.reloader.stage.StageStatus;
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
public class ReloaderService {
    private static final Logger log = LoggerFactory.getLogger(ReloaderService.class);

    private final ExternalDbConfig externalDbConfig;
    private final RefDbService refDbService;
    private final RefDbProperties refDbProperties;

    public ReloaderService(ExternalDbConfig externalDbConfig, RefDbService refDbService, RefDbProperties refDbProperties) {
        this.externalDbConfig = externalDbConfig;
        this.refDbService = refDbService;
        this.refDbProperties = refDbProperties;
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

        try {
            List<PayloadCandidate> discovered = discoverPayloads(site, startDate, endDate, testerType, dataType);
            if (discovered.isEmpty()) {
                return "No payloads discovered for " + site;
            }
            refDbService.stagePayloads(site, senderId, discovered);
            log.info("Staged {} payloads for site {} sender {}", discovered.size(), site, senderId);
            return String.format("Staged %d payloads for %s. Dispatch threshold is %d per run.",
                    discovered.size(), site, refDbProperties.getDispatch().getPerSend());
        } catch (SQLException ex) {
            log.error("Failed discovering payloads for site {}", site, ex);
            return "Error during discovery: " + ex.getMessage();
        }
    }

    public List<StageStatus> getStageStatuses() {
        return refDbService.fetchStatuses();
    }

    private List<PayloadCandidate> discoverPayloads(String site, String startDate, String endDate, String testerType, String dataType) throws SQLException {
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

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}