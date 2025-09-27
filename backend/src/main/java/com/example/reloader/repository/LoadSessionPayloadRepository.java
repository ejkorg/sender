package com.example.reloader.repository;

import com.example.reloader.entity.LoadSessionPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoadSessionPayloadRepository extends JpaRepository<LoadSessionPayload, Long> {
    List<LoadSessionPayload> findBySessionId(Long sessionId);
}
