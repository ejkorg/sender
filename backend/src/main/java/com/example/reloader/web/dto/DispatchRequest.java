package com.example.reloader.web.dto;

public record DispatchRequest(
        String site,
        Integer senderId,
        Integer limit
) {}
