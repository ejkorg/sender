package com.example.reloader.web.dto;

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
        String processedAt
) {}
