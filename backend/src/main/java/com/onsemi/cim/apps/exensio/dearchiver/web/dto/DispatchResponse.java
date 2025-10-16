package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

public record DispatchResponse(
        String site,
        Integer senderId,
        int dispatched
) {}
