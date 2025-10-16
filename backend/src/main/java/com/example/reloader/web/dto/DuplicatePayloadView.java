package com.example.reloader.web.dto;

public record DuplicatePayloadView(
        String metadataId,
        String dataId,
        String previousStatus,
        String processedAt,
        String stagedBy,
        String stagedAt,
        String lastRequestedBy,
        String lastRequestedAt,
        boolean requiresConfirmation
) {}
