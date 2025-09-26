package com.example.reloader.service;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.repository.ExternalMetadataRepository;
import com.example.reloader.entity.ExternalLocation;
import com.example.reloader.repository.ExternalLocationRepository;
import com.example.reloader.repository.MetadataRow;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataImporterService {
    private final Logger log = LoggerFactory.getLogger(MetadataImporterService.class);
    private final ExternalDbConfig externalDbConfig;
    private final SenderService senderService;
    private final MailService mailService;
    private final com.example.reloader.config.DiscoveryProperties discoveryProps;
    private final ExternalMetadataRepository externalMetadataRepository;
    private final ExternalLocationRepository externalLocationRepository;
    private final ExternalDbResolverService externalDbResolverService;

    public MetadataImporterService(ExternalDbConfig externalDbConfig, SenderService senderService, MailService mailService, com.example.reloader.config.DiscoveryProperties discoveryProps, ExternalMetadataRepository externalMetadataRepository, ExternalLocationRepository externalLocationRepository, ExternalDbResolverService externalDbResolverService) {
        this.externalDbConfig = externalDbConfig;
        this.senderService = senderService;
        this.mailService = mailService;
        this.discoveryProps = discoveryProps;
        this.externalMetadataRepository = externalMetadataRepository;
        this.externalLocationRepository = externalLocationRepository;
        this.externalDbResolverService = externalDbResolverService;
    }

    // Helper used by controller to find location by id
    public com.example.reloader.entity.ExternalLocation findLocationById(Long id) {
        return externalLocationRepository.findById(id).orElse(null);
    }

    public java.sql.Connection resolveConnectionForLocation(com.example.reloader.entity.ExternalLocation location, String environment) throws java.sql.SQLException {
        return externalDbResolverService.resolveConnectionForLocation(location, environment);
    }

    // Resolve a Connection directly by a configured connection key (db_connection_name)
    // This lets callers provide a connection key instead of a saved ExternalLocation id.
    public java.sql.Connection resolveConnectionForKey(String key, String environment) throws java.sql.SQLException {
        return externalDbConfig.getConnectionByKey(key, environment);
    }

    public java.util.List<com.example.reloader.repository.SenderCandidate> findSendersWithConnection(java.sql.Connection c, String location, String dataType, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.example.reloader.repository.JdbcExternalMetadataRepository) {
            return ((com.example.reloader.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findSendersWithConnection(c, location, dataType, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Sender lookup only supported by JDBC implementation");
    }

    // Distinct value helpers using an existing connection
    public java.util.List<String> findDistinctLocationsWithConnection(java.sql.Connection c, String dataType, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.example.reloader.repository.JdbcExternalMetadataRepository) {
            return ((com.example.reloader.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctLocationsWithConnection(c, dataType, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct locations supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctDataTypesWithConnection(java.sql.Connection c, String location, String testerType, String testPhase) {
        if (externalMetadataRepository instanceof com.example.reloader.repository.JdbcExternalMetadataRepository) {
            return ((com.example.reloader.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctDataTypesWithConnection(c, location, testerType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct data types supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctTesterTypesWithConnection(java.sql.Connection c, String location, String dataType, String testPhase) {
        if (externalMetadataRepository instanceof com.example.reloader.repository.JdbcExternalMetadataRepository) {
            return ((com.example.reloader.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctTesterTypesWithConnection(c, location, dataType, testPhase);
        }
        throw new UnsupportedOperationException("Distinct tester types supported only by JDBC implementation");
    }

    public java.util.List<String> findDistinctTestPhasesWithConnection(java.sql.Connection c, String location, String dataType, String testerType) {
        if (externalMetadataRepository instanceof com.example.reloader.repository.JdbcExternalMetadataRepository) {
            return ((com.example.reloader.repository.JdbcExternalMetadataRepository) externalMetadataRepository).findDistinctTestPhasesWithConnection(c, location, dataType, testerType);
        }
        throw new UnsupportedOperationException("Distinct test phases supported only by JDBC implementation");
    }

    /**
     * Discover metadata rows from external site and enqueue into local sender queue.
     * Returns number enqueued.
     */
    public int discoverAndEnqueue(String site, String environment, Integer senderId, String startDate, String endDate,
                                  String testerType, String dataType, String testPhase, String location, Long locationId, boolean writeListFile,
                                  int numberOfDataToSend, int countLimitTrigger) {
        // We'll stream results from repository, write list-file progressively (if requested), and enqueue in batches.
        final int batchSize = 200;
        final List<String> batch = new ArrayList<>(batchSize);
        Path listFilePath = null;
        final BufferedWriter[] bwRef = new BufferedWriter[1];
        final int[] discoveredCount = {0};
        final int[] addedCount = {0};
        final java.util.List<String> skippedOverall = new java.util.ArrayList<>();

        try {
            if (writeListFile) {
                listFilePath = Path.of(String.format("sender_list_%s.txt", senderId == null ? "0" : senderId.toString()));
                bwRef[0] = Files.newBufferedWriter(listFilePath, StandardCharsets.UTF_8);
            }

            // parse timestamps into LocalDateTime for repository
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
            LocalDateTime lstart = null;
            LocalDateTime lend = null;
            try { lstart = (startDate == null || startDate.isBlank()) ? LocalDateTime.parse("1970-01-01 00:00:00.000000", dtf) : LocalDateTime.parse(startDate, dtf); } catch (Exception e) { try { lstart = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ex) { lstart = LocalDateTime.of(1970,1,1,0,0); } }
            try { lend = (endDate == null || endDate.isBlank()) ? LocalDateTime.parse("2099-12-31 23:59:59.999999", dtf) : LocalDateTime.parse(endDate, dtf); } catch (Exception e) { try { lend = LocalDateTime.parse(endDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ex) { lend = LocalDateTime.of(2099,12,31,23,59,59); } }

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
                        cps.setString(1, senderId == null ? "" : senderId.toString());
                        try (ResultSet crs = cps.executeQuery()) {
                            if (crs.next()) {
                                int existing = crs.getInt(1);
                                log.info("External queue size for sender {} is {}", senderId, existing);
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
                        cps.setString(1, senderId == null ? "" : senderId.toString());
                        try (ResultSet crs = cps.executeQuery()) {
                            if (crs.next()) {
                                int existing = crs.getInt(1);
                                log.info("External queue size for sender {} is {}", senderId, existing);
                                if (existing >= countLimitTrigger) {
                                    log.info("Queue above threshold ({} >= {}), skipping discovery", existing, countLimitTrigger);
                                    return 0;
                                }
                            }
                        }
                    }
                }
            }

            final int maxToEnqueue = numberOfDataToSend > 0 ? numberOfDataToSend : Integer.MAX_VALUE;

            // Stream and enqueue
            if (locationId != null) {
                ExternalLocation loc = externalLocationRepository.findById(locationId).orElse(null);
                if (loc == null) {
                    log.warn("External location id {} not found, aborting discovery", locationId);
                } else {
                    try (Connection conn = externalDbResolverService.resolveConnectionForLocation(loc, environment)) {
                        externalMetadataRepository.streamMetadataWithConnection(conn, lstart, lend, dataType, testPhase, testerType, location, maxToEnqueue, mr -> {
                            discoveredCount[0]++;
                            String payload = (mr.getId() == null ? "" : mr.getId()) + "," + (mr.getIdData() == null ? "" : mr.getIdData());
                            if (bwRef[0] != null) {
                                try { bwRef[0].write(payload); bwRef[0].newLine(); } catch (Exception e) { log.warn("Failed writing to list file: {}", e.getMessage()); }
                            }
                            batch.add(payload);
                            if (batch.size() >= batchSize) {
                                SenderService.EnqueueResultHolder r = senderService.enqueuePayloadsWithResult(senderId, new ArrayList<>(batch), "metadata_discover");
                                addedCount[0] += (r == null ? 0 : r.enqueuedCount);
                                if (r != null && r.skippedPayloads != null && !r.skippedPayloads.isEmpty()) skippedOverall.addAll(r.skippedPayloads);
                                batch.clear();
                            }
                        });
                    }
                }
            } else {
                externalMetadataRepository.streamMetadata(site, environment, lstart, lend, dataType, testPhase, testerType, location, maxToEnqueue, mr -> {
                    discoveredCount[0]++;
                    String payload = (mr.getId() == null ? "" : mr.getId()) + "," + (mr.getIdData() == null ? "" : mr.getIdData());
                    if (bwRef[0] != null) {
                        try { bwRef[0].write(payload); bwRef[0].newLine(); } catch (Exception e) { log.warn("Failed writing to list file: {}", e.getMessage()); }
                    }
                    batch.add(payload);
                    if (batch.size() >= batchSize) {
                        SenderService.EnqueueResultHolder r = senderService.enqueuePayloadsWithResult(senderId, new ArrayList<>(batch), "metadata_discover");
                        addedCount[0] += (r == null ? 0 : r.enqueuedCount);
                        if (r != null && r.skippedPayloads != null && !r.skippedPayloads.isEmpty()) skippedOverall.addAll(r.skippedPayloads);
                        batch.clear();
                    }
                });
            }

            // enqueue remaining
            if (!batch.isEmpty()) {
                SenderService.EnqueueResultHolder r = senderService.enqueuePayloadsWithResult(senderId, new ArrayList<>(batch), "metadata_discover");
                addedCount[0] += (r == null ? 0 : r.enqueuedCount);
                if (r != null && r.skippedPayloads != null && !r.skippedPayloads.isEmpty()) skippedOverall.addAll(r.skippedPayloads);
                batch.clear();
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

        log.info("Discovered {} rows and enqueued {} payloads for sender {}. Skipped {} already-present.", discoveredCount[0], addedCount[0], senderId, skippedOverall.size());

        // Notification: prefer discovery properties, then fallback to env var
        String recipient = discoveryProps.getNotifyRecipient();
        if ((recipient == null || recipient.isBlank())) recipient = System.getenv("RELOADER_NOTIFY_RECIPIENT");
        if (recipient != null && !recipient.isBlank()) {
            String subj = String.format("Reloader: discovery complete for sender %s", senderId);
            StringBuilder body = new StringBuilder();
            body.append(String.format("Discovered %d rows and enqueued %d payloads for sender %s", discoveredCount[0], addedCount[0], senderId));
            if (!skippedOverall.isEmpty()) {
                body.append(". Skipped ").append(skippedOverall.size()).append(" already-present items:\n");
                int c = 0;
                for (String s : skippedOverall) {
                    if (c++ >= 50) { body.append("... (truncated)\n"); break; }
                    body.append(s).append("\n");
                }
            }
            boolean attach = discoveryProps.isNotifyAttachList();
            if (attach && listFilePath != null) {
                mailService.sendWithAttachment(recipient, subj, body.toString(), listFilePath);
            } else {
                mailService.send(recipient, subj, body.toString());
            }
        }

        return addedCount[0];
    }
}
