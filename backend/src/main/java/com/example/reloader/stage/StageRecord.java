package com.example.reloader.stage;

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
        Instant updatedAt
) {}
