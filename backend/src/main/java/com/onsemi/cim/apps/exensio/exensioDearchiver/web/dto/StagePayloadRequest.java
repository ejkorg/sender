package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record StagePayloadRequest(
        String site,
        String environment,
        Integer senderId,
        List<Payload> payloads,
    boolean triggerDispatch,
    boolean forceDuplicates
) {
    public record Payload(String metadataId, String dataId) {}
}
