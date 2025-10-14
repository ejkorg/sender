package com.example.reloader.stage;

import java.util.List;

public record StageResult(int stagedCount, List<String> skippedPayloads) {
    public StageResult {
        skippedPayloads = skippedPayloads == null ? List.of() : List.copyOf(skippedPayloads);
    }

    public static StageResult empty() {
        return new StageResult(0, List.of());
    }

    public StageResult merge(StageResult other) {
        if (other == null) {
            return this;
        }
        List<String> combined = new java.util.ArrayList<>(this.skippedPayloads);
        combined.addAll(other.skippedPayloads);
        return new StageResult(this.stagedCount + other.stagedCount, combined);
    }
}
