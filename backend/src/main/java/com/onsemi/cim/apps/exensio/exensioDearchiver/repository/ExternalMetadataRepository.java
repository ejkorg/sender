package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExternalMetadataRepository {
    List<MetadataRow> findMetadata(String site, String environment, LocalDateTime start, LocalDateTime end,
                                   String dataType, String testPhase, String testerType, String location, java.util.List<String> lots, java.util.List<String> wafers, int limit);

    List<MetadataRow> findMetadataPage(String site, String environment, LocalDateTime start, LocalDateTime end,
                                       String dataType, String testPhase, String testerType, String location, java.util.List<String> lots, java.util.List<String> wafers,
                                       int offset, int limit);

    long countMetadata(String site, String environment, LocalDateTime start, LocalDateTime end,
                       String dataType, String testPhase, String testerType, String location, java.util.List<String> lots, java.util.List<String> wafers);

    default String describePreviewQuery(LocalDateTime start,
                                        LocalDateTime end,
                                        String dataType,
                                        String testPhase,
                                        String testerType,
                                        String location,
                                        java.util.List<String> lots,
                                        java.util.List<String> wafers,
                                        int offset,
                                        int limit) {
        return null;
    }

    /**
     * Stream rows; consumer should be fast. This will use JDBC ResultSet iteration.
     */
    void streamMetadata(String site, String environment, LocalDateTime start, LocalDateTime end,
                        String dataType, String testPhase, String testerType, String location, java.util.List<String> lots, java.util.List<String> wafers, int limit,
                        java.util.function.Consumer<MetadataRow> consumer);

    /**
     * Stream rows using an existing JDBC Connection (caller is responsible for lifecycle).
     */
    void streamMetadataWithConnection(java.sql.Connection conn, LocalDateTime start, LocalDateTime end,
                                      String dataType, String testPhase, String testerType, String location, java.util.List<String> lots, java.util.List<String> wafers, int limit,
                                      java.util.function.Consumer<MetadataRow> consumer);

    /**
     * Find candidate senders from the external metadata DB using an existing Connection.
     * Returns list of pairs (id_sender, name).
     */
    java.util.List<SenderCandidate> findSendersWithConnection(java.sql.Connection conn,
                                                              String location,
                                                              String dataType,
                                                              String testerType,
                                                              String dataTypeExt,
                                                              String testPhase);

    /**
     * Describe the SQL that would be used for sender lookup (for debugging/logging).
     * Implementations may return the SQL text or null if not available.
     */
    default String describeSenderLookupQueryWithConnection(java.sql.Connection conn, String location, String dataType, String testerType, String dataTypeExt, String testPhase) {
        return null;
    }
    java.util.List<SenderCandidate> findAllSendersWithConnection(java.sql.Connection conn);

    // Distinct value helpers (use existing Connection lifecycle)
    java.util.List<String> findDistinctLocationsWithConnection(java.sql.Connection conn, String dataType, String testerType, String testPhase);

    java.util.List<String> findDistinctDataTypesWithConnection(java.sql.Connection conn, String location, String testerType, String testPhase);

    java.util.List<String> findDistinctTesterTypesWithConnection(java.sql.Connection conn, String location, String dataType, String testPhase);

    java.util.List<String> findDistinctDataTypeExtsWithConnection(java.sql.Connection conn, String location, String dataType, String testerType);

    java.util.List<String> findDistinctTestPhasesWithConnection(java.sql.Connection conn,
                                                                String location,
                                                                String dataType,
                                                                String testerType,
                                                                Integer senderId,
                                                                String senderName);
}
