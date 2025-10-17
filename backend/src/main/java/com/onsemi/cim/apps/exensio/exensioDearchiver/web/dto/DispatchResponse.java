package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

public record DispatchResponse(
        String site,
        Integer senderId,
        int dispatched
) {}
