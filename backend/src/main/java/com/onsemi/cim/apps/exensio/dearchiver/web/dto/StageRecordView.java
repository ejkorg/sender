package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

public record StageRecordView(
        long id,
        String site,
        int senderId,
        String metadataId,
        String dataId,
        String status,
        String errorMessage,
        String createdAt,
        String updatedAt,
        String processedAt,
        String stagedBy,
        String lastRequestedBy,
        String lastRequestedAt
) {}
