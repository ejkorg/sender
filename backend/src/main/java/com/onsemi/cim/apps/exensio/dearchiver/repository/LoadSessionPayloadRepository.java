package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoadSessionPayloadRepository extends JpaRepository<LoadSessionPayload, Long>, LoadSessionPayloadRepositoryCustom {
    List<LoadSessionPayload> findBySessionId(Long sessionId);

    List<LoadSessionPayload> findBySessionIdAndStatusOrderById(Long sessionId, String status, Pageable pageable);

    int countBySessionId(Long sessionId);

    int countBySessionIdAndStatus(Long sessionId, String status);

    java.util.Optional<com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload> findBySessionIdAndPayloadId(Long sessionId, String payloadId);

    long countBySessionIdAndPayloadId(Long sessionId, String payloadId);
}
