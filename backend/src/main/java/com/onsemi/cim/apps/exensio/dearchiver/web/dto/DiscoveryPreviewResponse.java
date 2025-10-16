package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

import java.util.List;

public record DiscoveryPreviewResponse(List<DiscoveryPreviewRow> items,
									   long total,
									   int page,
									   int size,
									   String debugSql) {}
