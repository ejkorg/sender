package com.onsemi.cim.apps.exensio.exensioDearchiver.stage;

import java.time.Instant;

/**
 * Represents a payload that was skipped during staging because it already exists.
 * Captures the previous status and completion metadata so callers can surface it to users.
 */
public record DuplicatePayload(
        String metadataId,
        String dataId,
        String previousStatus,
        Instant previousProcessedAt,
        String stagedBy,
        Instant stagedAt,
        String lastRequestedBy,
        Instant lastRequestedAt,
        boolean requiresConfirmation
) {
}
