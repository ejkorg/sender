package com.example.reloader.web.dto;

import java.util.List;

public record StagePayloadRequest(
        String site,
        String environment,
        Integer senderId,
        List<Payload> payloads,
        boolean triggerDispatch
) {
    public record Payload(String metadataId, String dataId) {}
}
