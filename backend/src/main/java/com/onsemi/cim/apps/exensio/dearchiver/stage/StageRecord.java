package com.onsemi.cim.apps.exensio.dearchiver.stage;

import java.time.Instant;

public record StageRecord(
        long id,
        String site,
        int senderId,
        String metadataId,
        String dataId,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant processedAt,
        String stagedBy,
        String lastRequestedBy,
        Instant lastRequestedAt
) {}
