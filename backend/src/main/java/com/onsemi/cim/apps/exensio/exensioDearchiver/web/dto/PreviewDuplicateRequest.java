package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record PreviewDuplicateRequest(
        String site,
        Integer senderId,
        List<PreviewItem> items
) {
    public static record PreviewItem(String metadataId, String dataId) {}
}
