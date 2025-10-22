package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record DiscoveryPreviewRequest(
        String site,
        String environment,
        String startDate,
        String endDate,
        List<String> lots,
        List<String> wafers,
        String testerType,
        String dataType,
        String testPhase,
        String location,
        int page,
        int size
) {}
