package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

public record DispatchRequest(
        String site,
        Integer senderId,
        Integer limit
) {}
