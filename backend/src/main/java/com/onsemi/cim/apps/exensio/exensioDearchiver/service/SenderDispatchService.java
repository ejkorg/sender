package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.exensioDearchiver.config.RefDbProperties;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
        if (records.isEmpty()) {
            return;
        }
        int maxQueueSize = properties.getDispatch().getMaxQueueSize();
        List<Long> success = new ArrayList<>();
        try (Connection connection = externalDbConfig.getConnection(site)) {
            boolean useSequence = requiresSequence(connection);
            List<StageRecord> toDispatch = records;
            if (maxQueueSize > 0) {
                int existing = safeCountQueue(connection, senderId);
                int available = maxQueueSize - existing;
                if (available <= 0) {
                    log.info("Queue for site {} sender {} already at capacity {} ({} existing)", site, senderId, maxQueueSize, existing);
                    return;
                }
                if (records.size() > available) {
                    log.info("Dispatch for site {} sender {} limited to {} of {} staged records due to queue threshold {}", site, senderId, available, records.size(), maxQueueSize);
                    toDispatch = new ArrayList<>(records.subList(0, available));
                }
            }

            if (toDispatch.isEmpty()) {
                return;
            }

            String insertSql;
            if (useSequence) {
                insertSql = "INSERT INTO DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created) VALUES (?, ?, ?, ?, ?)";
            } else {
                insertSql = "INSERT INTO DTP_SENDER_QUEUE_ITEM (id_metadata, id_data, id_sender, record_created) VALUES (?, ?, ?, ?)";
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                for (StageRecord record : toDispatch) {
                    try {
                        Timestamp now = Timestamp.from(Instant.now());
                        if (useSequence) {
                            long queueId = nextQueueId(connection);
                            insert.setLong(1, queueId);
                            insert.setString(2, record.metadataId());
                            insert.setString(3, record.dataId());
                            insert.setInt(4, senderId);
                            insert.setTimestamp(5, now);
                        } else {
                            insert.setString(1, record.metadataId());
                            insert.setString(2, record.dataId());
                            insert.setInt(3, senderId);
                            insert.setTimestamp(4, now);
                        }
                        insert.executeUpdate();
                        success.add(record.id());
                    } catch (SQLException ex) {
                        if (isDuplicate(ex)) {
                            log.info("Duplicate detected for {} – marking as enqueued", record);
                            success.add(record.id());
                        } else {
                            log.error("Failed pushing record {}", record, ex);
                            refDbService.markFailed(record.id(), ex.getMessage());
                        }
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
            refDbService.markEnqueued(success);
        }
    }

    private boolean isDuplicate(SQLException ex) {
        return ex.getErrorCode() == 1 || (ex.getMessage() != null && ex.getMessage().toUpperCase().contains("UNIQUE"));
    }

    private int safeCountQueue(Connection connection, int senderId) {
        String sql = "SELECT COUNT(1) FROM DTP_SENDER_QUEUE_ITEM WHERE id_sender = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed counting queue for sender {}: {}", senderId, ex.getMessage());
        }
        return 0;
    }

    private long nextQueueId(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        boolean oracle = productName != null && productName.toLowerCase(java.util.Locale.ROOT).contains("oracle");
        String sql = oracle ? "SELECT DTP_SENDER_QUEUE_ITEM_SEQ.NEXTVAL FROM dual" : "SELECT NEXT VALUE FOR DTP_SENDER_QUEUE_ITEM_SEQ";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Unable to fetch next value from DTP_SENDER_QUEUE_ITEM_SEQ");
    }

    private boolean requiresSequence(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName == null) {
                return false;
            }
            return productName.toLowerCase(java.util.Locale.ROOT).contains("oracle");
        } catch (SQLException ex) {
            log.warn("Failed resolving database product name; assuming identity inserts are supported: {}", ex.getMessage());
            return false;
        }
    }

    public int dispatchSender(String site, int senderId) {
        return dispatchSender(site, senderId, null);
    }

    public int dispatchSender(String site, int senderId, Integer limitOverride) {
        int configuredPerSend = properties.getDispatch().getPerSend();
        int defaultBatchSize = configuredPerSend > 0 ? configuredPerSend : 200;
        int remaining = (limitOverride != null && limitOverride > 0) ? limitOverride : Integer.MAX_VALUE;
        int processed = 0;

        while (true) {
            if (remaining <= 0) {
                break;
            }
            int requestedBatch = Math.min(defaultBatchSize, remaining);
            if (requestedBatch <= 0) {
                break;
            }
            List<StageRecord> batch = refDbService.fetchNextBatchForSender(site, senderId, requestedBatch);
            if (batch.isEmpty()) {
                break;
            }
            pushGroup(site, senderId, batch);
            processed += batch.size();
            remaining -= batch.size();
            if (batch.size() < requestedBatch) {
                break;
            }
        }
        return processed;
    }
}
