package com.example.reloader.service;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.config.RefDbProperties;
import com.example.reloader.stage.StageRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SenderDispatchService {
    private static final Logger log = LoggerFactory.getLogger(SenderDispatchService.class);

    private final RefDbService refDbService;
    private final ExternalDbConfig externalDbConfig;
    private final RefDbProperties properties;

    public SenderDispatchService(RefDbService refDbService, ExternalDbConfig externalDbConfig, RefDbProperties properties) {
        this.refDbService = refDbService;
        this.externalDbConfig = externalDbConfig;
        this.properties = properties;
    }

    @PostConstruct
    public void logStartup() {
        log.info("Sender dispatch service initialized with perSend={} intervalMs={}ms", properties.getDispatch().getPerSend(), properties.getDispatch().getIntervalMs());
    }

    @Scheduled(fixedDelayString = "${refdb.dispatch.interval-ms:60000}")
    public void dispatch() {
        try {
            Set<String> sites = refDbService.findSitesWithPending();
            if (sites.isEmpty()) {
                return;
            }
            for (String site : sites) {
                processSite(site);
            }
        } catch (Exception ex) {
            log.error("Dispatch run failed", ex);
        }
    }

    private void processSite(String site) {
        int limit = properties.getDispatch().getPerSend();
        List<StageRecord> batch = refDbService.fetchNextBatchForSite(site, limit);
        if (batch.isEmpty()) {
            return;
        }
        Map<Integer, List<StageRecord>> bySender = new HashMap<>();
        for (StageRecord record : batch) {
            bySender.computeIfAbsent(record.senderId(), key -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<Integer, List<StageRecord>> entry : bySender.entrySet()) {
            pushGroup(site, entry.getKey(), entry.getValue());
        }
    }

    private void pushGroup(String site, int senderId, List<StageRecord> records) {
        String insertSql = "INSERT INTO dtp_sender_queue (senderid, id_metadata, id_data, record_created) VALUES (?, ?, ?, SYSTIMESTAMP)";
        List<Long> success = new ArrayList<>();
        try (Connection connection = externalDbConfig.getConnection(site);
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (StageRecord record : records) {
                try {
                    ps.setInt(1, senderId);
                    ps.setString(2, record.metadataId());
                    ps.setString(3, record.dataId());
                    ps.executeUpdate();
                    success.add(record.id());
                } catch (SQLException ex) {
                    if (isDuplicate(ex)) {
                        log.info("Duplicate detected for {} – marking as sent", record);
                        success.add(record.id());
                    } else {
                        log.error("Failed pushing record {}", record, ex);
                        refDbService.markFailed(record.id(), ex.getMessage());
                    }
                }
            }
        } catch (SQLException ex) {
            log.error("Connection failure pushing site {} sender {}", site, senderId, ex);
            for (StageRecord record : records) {
                refDbService.markFailed(record.id(), ex.getMessage());
            }
            return;
        }
        if (!success.isEmpty()) {
            refDbService.markSent(success);
        }
    }

    private boolean isDuplicate(SQLException ex) {
        return ex.getErrorCode() == 1 || (ex.getMessage() != null && ex.getMessage().toUpperCase().contains("UNIQUE"));
    }
}
