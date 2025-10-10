package com.example.reloader.stage;

public record PayloadCandidate(String metadataId, String dataId) {
    public PayloadCandidate {
        if (metadataId == null || metadataId.isBlank()) {
            throw new IllegalArgumentException("metadataId is required");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new IllegalArgumentException("dataId is required");
        }
    }
}
