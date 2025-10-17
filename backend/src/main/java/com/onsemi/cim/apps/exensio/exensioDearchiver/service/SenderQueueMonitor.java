package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.exensioDearchiver.config.RefDbProperties;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Periodically inspects the external sender queue and marks staged payloads as completed once their
 * queue entries disappear. This enables the UI to surface completion timestamps for previously
 * processed payloads without blocking re-processing.
 */
@Service
public class SenderQueueMonitor {
    private static final Logger log = LoggerFactory.getLogger(SenderQueueMonitor.class);

    private final RefDbService refDbService;
    private final ExternalDbConfig externalDbConfig;
    private final RefDbProperties properties;

    public SenderQueueMonitor(RefDbService refDbService,
                              ExternalDbConfig externalDbConfig,
                              RefDbProperties properties) {
        this.refDbService = refDbService;
        this.externalDbConfig = externalDbConfig;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${refdb.dispatch.monitor-interval-ms:120000}")
    public void monitorQueue() {
        int batchSize = Math.max(properties.getDispatch().getPerSend(), 100);
        List<StageRecord> pending = refDbService.findEnqueuedWithoutProcessed(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        Map<String, Map<Integer, List<StageRecord>>> bySite = partitionBySiteAndSender(pending);
        for (Map.Entry<String, Map<Integer, List<StageRecord>>> entry : bySite.entrySet()) {
            String site = entry.getKey();
            Map<Integer, List<StageRecord>> bySender = entry.getValue();
            Connection connection = null;
            try {
                connection = externalDbConfig.getConnection(site);
                if (connection == null) {
                    log.debug("Skipping monitor for site {} because no external connection is available", site);
                    continue;
                }
                for (Map.Entry<Integer, List<StageRecord>> senderEntry : bySender.entrySet()) {
                    inspectQueue(connection, site, senderEntry.getKey(), senderEntry.getValue());
                }
            } catch (SQLException ex) {
                log.warn("Monitor unable to inspect queue for site {}: {}", site, ex.getMessage());
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException closeEx) {
                        log.debug("Failed closing monitor connection for site {}: {}", site, closeEx.getMessage());
                    }
                }
            }
        }
    }

    private void inspectQueue(Connection connection, String site, int senderId, List<StageRecord> records) {
        if (connection == null) {
            log.debug("Skipping monitor for site {} sender {} due to missing connection", site, senderId);
            return;
        }
        Set<String> queueKeys = fetchQueueKeys(connection, senderId);
        if (queueKeys.isEmpty()) {
            log.debug("Queue empty for site {} sender {} when monitoring {} staged entries", site, senderId, records.size());
        }
        List<Long> completed = new ArrayList<>();
        for (StageRecord record : records) {
            String key = buildKey(record.metadataId(), record.dataId());
            if (!queueKeys.contains(key)) {
                completed.add(record.id());
            }
        }
        if (!completed.isEmpty()) {
            refDbService.markCompleted(completed, Instant.now());
            log.info("Marked {} staged payloads complete for site {} sender {}", completed.size(), site, senderId);
        }
    }

    private Set<String> fetchQueueKeys(Connection connection, int senderId) {
        Set<String> keys = new HashSet<>();
        if (connection == null) {
            return keys;
        }
        String sql = "SELECT id_metadata, id_data FROM DTP_SENDER_QUEUE_ITEM WHERE id_sender = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(buildKey(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed reading queue entries for sender {}: {}", senderId, ex.getMessage());
        }
        return keys;
    }

    private Map<String, Map<Integer, List<StageRecord>>> partitionBySiteAndSender(List<StageRecord> records) {
        Map<String, Map<Integer, List<StageRecord>>> result = new HashMap<>();
        for (StageRecord record : records) {
            result.computeIfAbsent(record.site(), key -> new HashMap<>())
                    .computeIfAbsent(record.senderId(), key -> new ArrayList<>())
                    .add(record);
        }
        return result;
    }

    private String buildKey(String metadataId, String dataId) {
        String meta = metadataId == null ? "" : metadataId;
        String data = dataId == null ? "" : dataId;
        return meta + "|" + data;
    }
}
