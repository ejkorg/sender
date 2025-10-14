package com.example.reloader.web.dto;

import java.util.List;

public record StagePayloadResponse(int staged, int duplicates, List<String> duplicatePayloads, int dispatched) {}
