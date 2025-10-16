package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import java.util.List;

public interface LoadSessionPayloadRepositoryCustom {
    List<LoadSessionPayload> claimNextBatch(Long sessionId, int batchSize);
}
