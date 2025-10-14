package com.example.reloader.web.dto;

import java.util.List;

public record DiscoveryPreviewResponse(List<DiscoveryPreviewRow> items, long total, int page, int size) {}
