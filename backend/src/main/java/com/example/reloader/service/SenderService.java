package com.example.reloader.service;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.entity.SenderQueueEntry;
import com.example.reloader.repository.SenderQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class SenderService {
    private final Logger log = LoggerFactory.getLogger(SenderService.class);
    private final SenderQueueRepository repository;
    private final ExternalDbConfig externalDbConfig;

    public SenderService(SenderQueueRepository repository, ExternalDbConfig externalDbConfig) {
        this.repository = repository;
        this.externalDbConfig = externalDbConfig;
    }

    @Scheduled(cron = "${app.sender.cron:0 */5 * * * *}")
    public void scheduledRun() {
        runIfBelowThreshold();
    }

    @Transactional
    public void runIfBelowThreshold() {
        long pending = repository.countByStatus("NEW");
        log.info("Pending items in queue (NEW): {}", pending);
        // threshold properties could be externalized; using hard-coded for now
        long threshold = 600;
        if (pending > threshold) {
            log.info("Queue above threshold ({} > {}). Sender will not run.", pending, threshold);
            return;
        }
        int limit = 300;
        processBatch(limit);
    }

    @Transactional
    public void processBatch(int limit) {
        List<SenderQueueEntry> batch = repository.findByStatusOrderByCreatedAt("NEW", PageRequest.of(0, limit));
        if (batch == null || batch.isEmpty()) {
            log.debug("No NEW items to process.");
            return;
        }

        for (SenderQueueEntry e : batch) {
            try {
                e.setStatus("PROCESSING");
                repository.save(e);

                // If external sending is needed, call external DB / API here.
                // For now mark as SENT
                e.setStatus("SENT");
                e.setProcessedAt(Instant.now());
                repository.save(e);
                log.info("Processed payload {} as SENT", e.getPayloadId());
            } catch (Exception ex) {
                log.error("Failed processing payload {}: {}", e.getPayloadId(), ex.getMessage());
                e.setStatus("FAILED");
                repository.save(e);
            }
        }
    }

    @Transactional
    public int enqueuePayloads(Integer senderId, java.util.List<String> payloadIds, String source) {
        EnqueueResultHolder res = enqueuePayloadsWithResult(senderId, payloadIds, source);
        return res.enqueuedCount;
    }

    @Transactional
    public EnqueueResultHolder enqueuePayloadsWithResult(Integer senderId, java.util.List<String> payloadIds, String source) {
        if (payloadIds == null || payloadIds.isEmpty()) return new EnqueueResultHolder(0, java.util.Collections.emptyList());

        // normalize and trim input payloads
        java.util.List<String> normalized = new java.util.ArrayList<>();
        for (String p : payloadIds) {
            if (p == null) continue;
            String t = p.trim();
            if (t.isEmpty()) continue;
            normalized.add(t);
        }
        if (normalized.isEmpty()) return new EnqueueResultHolder(0, java.util.Collections.emptyList());

        // Query repository for payloads already present for this sender
        java.util.List<String> existing = repository.findPayloadIdsBySenderIdAndPayloadIdIn(senderId, normalized);
        java.util.Set<String> existingSet = new java.util.HashSet<>(existing == null ? java.util.Collections.emptyList() : existing);

        java.util.List<String> toInsert = new java.util.ArrayList<>();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        for (String p : normalized) {
            if (existingSet.contains(p)) skipped.add(p);
            else toInsert.add(p);
        }

        int inserted = 0;
        for (String p : toInsert) {
            SenderQueueEntry entry = new SenderQueueEntry(senderId, p, source);
            entry.setStatus("NEW");
            try {
                repository.save(entry);
                inserted++;
            } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                // Unique constraint violation or other integrity issue means another process
                // inserted the same payload concurrently. Treat as skipped.
                log.warn("Skipping payload due to integrity violation (probably duplicate): {}", p);
                skipped.add(p);
            }
        }
        log.info("Enqueued {} payloads (senderId={}, source={}), skipped {} already-present", inserted, senderId, source, skipped.size());
        return new EnqueueResultHolder(inserted, skipped);
    }

    // Example helper: when caller wants to push items directly into an external sender queue table on remote DB
    public int pushToExternalQueue(String site, Integer senderId, List<SenderQueueEntry> items) {
        try (Connection c = externalDbConfig.getConnection(site)) {
            // simplistic insertion example; caller must ensure schema compatibility
            String insertSql = "insert into DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created) values (DTP_SENDER_QUEUE_ITEM_SEQ.nextval, ?, ?, ?, ?)";
            PreparedStatement ps = c.prepareStatement(insertSql);
            int pushed = 0;
            for (SenderQueueEntry e : items) {
                String[] parts = e.getPayloadId().split(",");
                if (parts.length >= 2) {
                    ps.setString(1, parts[0]);
                    ps.setString(2, parts[1]);
                    ps.setInt(3, senderId);
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                    pushed++;
                }
            }
            return pushed;
        } catch (Exception ex) {
            log.error("Error pushing to external queue for site {}: {}", site, ex.getMessage());
            return 0;
        }
    }

    public java.util.List<SenderQueueEntry> getQueue(Integer senderId, String status, int limit) {
        return repository.findBySenderIdAndStatusOrderByCreatedAt(senderId, status, PageRequest.of(0, limit));
    }

    // Internal holder used to return both count and skipped items to callers.
    public static class EnqueueResultHolder {
        public final int enqueuedCount;
        public final java.util.List<String> skippedPayloads;

        public EnqueueResultHolder(int enqueuedCount, java.util.List<String> skippedPayloads) {
            this.enqueuedCount = enqueuedCount;
            this.skippedPayloads = skippedPayloads == null ? java.util.Collections.emptyList() : skippedPayloads;
        }
    }
}
