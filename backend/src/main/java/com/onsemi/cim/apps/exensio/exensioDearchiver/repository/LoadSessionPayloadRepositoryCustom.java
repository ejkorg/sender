package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSessionPayload;
import java.util.List;

public interface LoadSessionPayloadRepositoryCustom {
    List<LoadSessionPayload> claimNextBatch(Long sessionId, int batchSize);
}
