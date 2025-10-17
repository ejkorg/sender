package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record DiscoveryPreviewResponse(List<DiscoveryPreviewRow> items,
									   long total,
									   int page,
									   int size,
									   String debugSql) {}
