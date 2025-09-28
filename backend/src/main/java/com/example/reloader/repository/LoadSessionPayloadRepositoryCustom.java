package com.example.reloader.repository;

import com.example.reloader.entity.LoadSessionPayload;
import java.util.List;

public interface LoadSessionPayloadRepositoryCustom {
    List<LoadSessionPayload> claimNextBatch(Long sessionId, int batchSize);
}
