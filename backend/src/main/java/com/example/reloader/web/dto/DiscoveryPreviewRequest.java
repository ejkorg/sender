package com.example.reloader.web.dto;

public record DiscoveryPreviewRequest(
        String site,
        String environment,
        String startDate,
        String endDate,
        String testerType,
        String dataType,
        String testPhase,
        String location,
        int page,
        int size
) {}
