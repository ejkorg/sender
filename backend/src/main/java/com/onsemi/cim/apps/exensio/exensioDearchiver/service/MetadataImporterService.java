package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.ExternalLocationRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.ExternalMetadataRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.MetadataRow;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.DuplicatePayload;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.PayloadCandidate;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageResult;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DiscoveryPreviewResponse;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DiscoveryPreviewRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataImporterService {
    private final Logger log = LoggerFactory.getLogger(MetadataImporterService.class);
    private static final DateTimeFormatter FMT_MICROS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter FMT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final LocalDateTime START_FALLBACK = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime END_FALLBACK = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
    private final ExternalDbConfig externalDbConfig;
    private final RefDbService refDbService;
    private final SenderService senderService;
    private final MailService mailService;
    private final com.onsemi.cim.apps.exensio.exensioDearchiver.config.DiscoveryProperties discoveryProps;
    private final ExternalMetadataRepository externalMetadataRepository;
    private final ExternalLocationRepository externalLocationRepository;
    private final ExternalDbResolverService externalDbResolverService;
    private final org.springframework.core.env.Environment env;

    public MetadataImporterService(ExternalDbConfig externalDbConfig,
                                   RefDbService refDbService,
                                   SenderService senderService,
                                   MailService mailService,
                                   com.onsemi.cim.apps.exensio.exensioDearchiver.config.DiscoveryProperties discoveryProps,
                                   ExternalMetadataRepository externalMetadataRepository,
                                   ExternalLocationRepository externalLocationRepository,
                                   ExternalDbResolverService externalDbResolverService,
                                   org.springframework.core.env.Environment env) {
        this.externalDbConfig = externalDbConfig;
        this.refDbService = refDbService;
        this.senderService = senderService;
        this.mailService = mailService;
        this.discoveryProps = discoveryProps;
        this.externalMetadataRepository = externalMetadataRepository;
        this.externalLocationRepository = externalLocationRepository;
        this.externalDbResolverService = externalDbResolverService;
        this.env = env;
    }

    // Helper used by controller to find location by id
    public com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation findLocationById(Long id) {
        return externalLocationRepository.findById(id).orElse(null);
    }

    public java.sql.Connection resolveConnectionForLocation(com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation location, String environment) throws java.sql.SQLException {
        return externalDbResolverService.resolveConnectionForLocation(location, environment);
    }

    // Resolve a Connection directly by a configured connection key (db_connection_name)
    // This lets callers provide a connection key instead of a saved ExternalLocation id.
    public java.sql.Connection resolveConnectionForKey(String key, String environment) throws java.sql.SQLException {
        return externalDbConfig.getConnectionByKey(key, environment);
    }

    public java.util.List<com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate> findSendersWithConnection(java.sql.Connection c, String location, String dataType, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findSendersWithConnection(c, location, dataType, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Sender lookup only supported by JDBC implementation");
    }

    public java.util.List<com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate> findAllSendersWithConnection(java.sql.Connection c) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findAllSendersWithConnection(c);
        }
        throw new UnsupportedOperationException("Sender list only supported by JDBC implementation");
    }

    // Distinct value helpers using an existing connection
    public java.util.List<String> findDistinctLocationsWithConnection(java.sql.Connection c, String dataType, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctLocationsWithConnection(c, dataType, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct locations supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctDataTypesWithConnection(java.sql.Connection c, String location, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctDataTypesWithConnection(c, location, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct data types supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctTesterTypesWithConnection(java.sql.Connection c, String location, String dataType, String testPhase) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctTesterTypesWithConnection(c, location, dataType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct tester types supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctTestPhasesWithConnection(java.sql.Connection c, String location, String dataType, String testerType, Integer senderId, String senderName) {
        if (externalMetadataRepository instanceof com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) {
            return ((com.onsemi.cim.apps.exensio.exensioDearchiver.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctTestPhasesWithConnection(c, location, dataType, testerType, senderId, senderName);
        }
        throw new UnsupportedOperationException("Distinct test phases supported only by JDBC implementation");
    }

    /**
     * Discover metadata rows from external site and enqueue into local sender queue.
     * Returns number enqueued.
     */
    public DiscoveryPreviewResponse previewMetadata(String site, String environment, Integer senderId,
                                                    String startDate, String endDate,
                                                    java.util.List<String> lots, java.util.List<String> wafers,
                                                    String testerType, String dataType, String testPhase,
                                                    String location, int page, int size) {
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("site is required");
        }
        if (senderId == null || senderId <= 0) {
            throw new IllegalArgumentException("senderId is required");
        }
        String resolvedEnv = (environment == null || environment.isBlank()) ? "qa" : environment;
        int resolvedSize = size <= 0 ? 50 : Math.min(size, 500);
        int resolvedPage = Math.max(page, 0);
        int offset = resolvedPage * resolvedSize;

        LocalDateTime lstart = resolveStart(startDate);
        LocalDateTime lend = resolveEnd(endDate);

    long total = externalMetadataRepository.countMetadata(site, resolvedEnv, lstart, lend, dataType, testPhase, testerType, location, lots, wafers);
    List<MetadataRow> rows = externalMetadataRepository.findMetadataPage(site, resolvedEnv, lstart, lend, dataType, testPhase, testerType, location, lots, wafers, offset, resolvedSize);
    String debugSql = externalMetadataRepository.describePreviewQuery(lstart, lend, dataType, testPhase, testerType, location, lots, wafers, offset, resolvedSize);

        List<DiscoveryPreviewRow> items = rows.stream()
            .map(row -> new DiscoveryPreviewRow(
                nullSafe(row.getId()),
                nullSafe(row.getIdData()),
                nullSafe(row.getLot()),
                nullSafe(row.getWafer()),
                nullSafe(row.getOriginalFileName()),
                toIsoString(row.getEndTime())
            ))
            .toList();

        if (log.isDebugEnabled()) {
            log.debug("Preview result total={} page={} size={} returned={}", total, resolvedPage, resolvedSize, items.size());
        }
        return new DiscoveryPreviewResponse(items, total, resolvedPage, resolvedSize, debugSql);
    }

    public int discoverAndEnqueue(String site, String environment, Integer senderId, String startDate, String endDate,
                                  String testerType, String dataType, String testPhase, String location, Long locationId, boolean writeListFile,
                                  int numberOfDataToSend, int countLimitTrigger) {
        if (senderId == null || senderId <= 0) {
            log.warn("senderId is required to stage discovery results (site={}, environment={})", site, environment);
            return 0;
        }

        final int resolvedSenderId = senderId;
        final int batchSize = 200;
        final List<PayloadCandidate> batch = new ArrayList<>(batchSize);
        Path listFilePath = null;
        final BufferedWriter[] bwRef = new BufferedWriter[1];
        final int[] discoveredCount = {0};
        final int[] stagedCount = {0};
        final java.util.List<DuplicatePayload> duplicatesOverall = new java.util.ArrayList<>();
        final java.util.List<String> enqueuePayloadIds = new java.util.ArrayList<>();

        try {
            if (writeListFile) {
                listFilePath = Path.of(String.format("sender_list_%s.txt", Integer.toString(resolvedSenderId)));
                bwRef[0] = Files.newBufferedWriter(listFilePath, StandardCharsets.UTF_8);
            }

            LocalDateTime lstart = resolveStart(startDate);
            LocalDateTime lend = resolveEnd(endDate);

            // Pre-check external queue size via resolved external connection
            if (locationId != null) {
                ExternalLocation loc = externalLocationRepository.findById(locationId).orElse(null);
                if (loc == null) {
                    log.warn("External location id {} not found, aborting discovery", locationId);
                    return 0;
                }
                try (Connection c = externalDbResolverService.resolveConnectionForLocation(loc, environment)) {
                    String countSql = "select count(id) as count from DTP_SENDER_QUEUE_ITEM where id_sender=?";
                    try (PreparedStatement cps = c.prepareStatement(countSql)) {
                        cps.setString(1, Integer.toString(resolvedSenderId));
                        try (ResultSet crs = cps.executeQuery()) {
                            if (crs.next()) {
                                int existing = crs.getInt(1);
                                log.info("External queue size for sender {} is {}", resolvedSenderId, existing);
                                if (existing >= countLimitTrigger) {
                                    log.info("Queue above threshold ({} >= {}), skipping discovery", existing, countLimitTrigger);
                                    return 0;
                                }
                            }
                        }
                    }
                }
            } else {
                try (Connection c = externalDbConfig.getConnection(site, environment)) {
                    String countSql = "select count(id) as count from DTP_SENDER_QUEUE_ITEM where id_sender=?";
                    try (PreparedStatement cps = c.prepareStatement(countSql)) {
                        cps.setString(1, Integer.toString(resolvedSenderId));
                        try (ResultSet crs = cps.executeQuery()) {
                            if (crs.next()) {
                                int existing = crs.getInt(1);
                                log.info("External queue size for sender {} is {}", resolvedSenderId, existing);
                                if (existing >= countLimitTrigger) {
                                    log.info("Queue above threshold ({} >= {}), skipping discovery", existing, countLimitTrigger);
                                    return 0;
                                }
                            }
                        }
                    }
                }
            }

            final int maxToStage = numberOfDataToSend > 0 ? numberOfDataToSend : Integer.MAX_VALUE;

            java.util.function.Consumer<MetadataRow> processor = mr -> {
                discoveredCount[0]++;
                String metadataIdValue = mr.getId();
                String dataIdValue = mr.getIdData();
                String payload = (metadataIdValue == null ? "" : metadataIdValue) + "," + (dataIdValue == null ? "" : dataIdValue);
                if (bwRef[0] != null) {
                    try { bwRef[0].write(payload); bwRef[0].newLine(); } catch (Exception e) { log.warn("Failed writing to list file: {}", e.getMessage()); }
                }
                if (metadataIdValue == null || metadataIdValue.isBlank() || dataIdValue == null || dataIdValue.isBlank()) {
                    return;
                }
                enqueuePayloadIds.add(payload);
                batch.add(new PayloadCandidate(metadataIdValue, dataIdValue));
                if (batch.size() >= batchSize) {
                    StageResult result = stageCurrentBatch(site, resolvedSenderId, batch);
                    stagedCount[0] += result.stagedCount();
                    if (!result.duplicates().isEmpty()) {
                        duplicatesOverall.addAll(result.duplicates());
                    }
                }
            };

            // Stream and stage
            if (locationId != null) {
                ExternalLocation loc = externalLocationRepository.findById(locationId).orElse(null);
                if (loc == null) {
                    log.warn("External location id {} not found, aborting discovery", locationId);
                } else {
                    try (Connection conn = externalDbResolverService.resolveConnectionForLocation(loc, environment)) {
                        externalMetadataRepository.streamMetadataWithConnection(conn, lstart, lend, dataType, testPhase, testerType, location, null, null, maxToStage, processor);
                    }
                }
            } else {
                externalMetadataRepository.streamMetadata(site, environment, lstart, lend, dataType, testPhase, testerType, location, null, null, maxToStage, processor);
            }

            StageResult tail = stageCurrentBatch(site, resolvedSenderId, batch);
            stagedCount[0] += tail.stagedCount();
            if (!tail.duplicates().isEmpty()) {
                duplicatesOverall.addAll(tail.duplicates());
            }

        } catch (Exception ex) {
            log.error("Failed to discover metadata from site {}: {}", site, ex.getMessage(), ex);
            return 0;
        } finally {
            if (bwRef[0] != null) try { bwRef[0].close(); } catch (Exception ignore) {}
        }

        if (discoveredCount[0] == 0) {
            log.info("No metadata rows discovered for given criteria");
            return 0;
        }

        if (!enqueuePayloadIds.isEmpty()) {
            try {
                senderService.enqueuePayloadsWithResult(resolvedSenderId, enqueuePayloadIds, "metadata_discover");
            } catch (Exception ex) {
                log.warn("Failed enqueueing {} payloads for sender {} after discovery: {}", enqueuePayloadIds.size(), resolvedSenderId, ex.getMessage());
            }
        }

    log.info("Discovered {} rows and staged {} payloads for sender {}. Skipped {} duplicates.", discoveredCount[0], stagedCount[0], resolvedSenderId, duplicatesOverall.size());

        // Notification: prefer discovery properties, then fallback to env var
        String recipient = discoveryProps.getNotifyRecipient();
        if (recipient == null || recipient.isBlank()) {
            recipient = com.onsemi.cim.apps.exensio.exensioDearchiver.config.ConfigUtils.getString(env, "reloader.notify-recipient", "RELOADER_NOTIFY_RECIPIENT", null);
        }
        if (recipient != null && !recipient.isBlank()) {
            String subj = String.format("Reloader: discovery complete for sender %s", resolvedSenderId);
            StringBuilder body = new StringBuilder();
            body.append(String.format("Discovered %d rows and staged %d payloads for sender %s", discoveredCount[0], stagedCount[0], resolvedSenderId));
            if (!duplicatesOverall.isEmpty()) {
                body.append(". Skipped ").append(duplicatesOverall.size()).append(" duplicate items:\n");
                int c = 0;
                for (DuplicatePayload duplicate : duplicatesOverall) {
                    if (c++ >= 50) { body.append("... (truncated)\n"); break; }
                    body.append(formatDuplicateForNotification(duplicate)).append("\n");
                }
            }
            boolean attach = discoveryProps.isNotifyAttachList();
            if (attach && listFilePath != null) {
                mailService.sendWithAttachment(recipient, subj, body.toString(), listFilePath);
            } else {
                mailService.send(recipient, subj, body.toString());
            }
        }

        return stagedCount[0];
    }

    private StageResult stageCurrentBatch(String site, int senderId, List<PayloadCandidate> batch) {
        if (batch == null || batch.isEmpty()) {
            return StageResult.empty();
        }
        try {
            return refDbService.stagePayloads(site, senderId, "metadata", new ArrayList<>(batch), true);
        } finally {
            batch.clear();
        }
    }

    private LocalDateTime resolveStart(String value) {
        return parseDateOrDefault(value, START_FALLBACK);
    }

    private LocalDateTime resolveEnd(String value) {
        return parseDateOrDefault(value, END_FALLBACK);
    }

    private LocalDateTime parseDateOrDefault(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value, FMT_MICROS);
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(value, FMT_SECONDS);
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    private String toIsoString(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String formatDuplicateForNotification(DuplicatePayload duplicate) {
        StringBuilder sb = new StringBuilder();
        sb.append(duplicate.metadataId()).append(",").append(duplicate.dataId());
        if (duplicate.previousStatus() != null && !duplicate.previousStatus().isBlank()) {
            sb.append(" status=").append(duplicate.previousStatus());
        }
        if (duplicate.previousProcessedAt() != null) {
            sb.append(" processedAt=").append(duplicate.previousProcessedAt());
        }
        return sb.toString();
    }

    private String nullSafe(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
