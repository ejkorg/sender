package com.example.reloader.web.dto;

public record DispatchResponse(
        String site,
        Integer senderId,
        int dispatched
) {}
