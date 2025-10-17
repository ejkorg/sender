package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

public record DispatchRequest(
        String site,
        Integer senderId,
        Integer limit
) {}
