package com.example.reloader.stage;

import java.util.ArrayList;
import java.util.List;

public record StageResult(int stagedCount, List<DuplicatePayload> duplicates) {
    public StageResult {
        duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
    }

    public static StageResult empty() {
        return new StageResult(0, List.of());
    }

    public StageResult merge(StageResult other) {
        if (other == null) {
            return this;
        }
        List<DuplicatePayload> combined = new ArrayList<>(this.duplicates);
        combined.addAll(other.duplicates);
        return new StageResult(this.stagedCount + other.stagedCount, combined);
    }
}
