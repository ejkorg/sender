package com.example.reloader.web.dto;

import java.util.List;

public class EnqueueResult {
    private int enqueuedCount;
    private List<String> skippedPayloads;

    public EnqueueResult() {}

    public EnqueueResult(int enqueuedCount, List<String> skippedPayloads) {
        this.enqueuedCount = enqueuedCount;
        this.skippedPayloads = skippedPayloads;
    }

    public int getEnqueuedCount() { return enqueuedCount; }
    public void setEnqueuedCount(int enqueuedCount) { this.enqueuedCount = enqueuedCount; }
    public List<String> getSkippedPayloads() { return skippedPayloads; }
    public void setSkippedPayloads(List<String> skippedPayloads) { this.skippedPayloads = skippedPayloads; }
}
