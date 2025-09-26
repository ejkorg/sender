package com.example.reloader.repository;

import com.example.reloader.entity.SenderQueueEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SenderQueueRepository extends JpaRepository<SenderQueueEntry, Long> {
    long countByStatus(String status);

    @Query("select s from SenderQueueEntry s where s.status = :status order by s.createdAt asc")
    List<SenderQueueEntry> findByStatusOrderByCreatedAt(@Param("status") String status, Pageable p);

    @Query("select s from SenderQueueEntry s where s.senderId = :senderId and s.status = :status order by s.createdAt asc")
    List<SenderQueueEntry> findBySenderIdAndStatusOrderByCreatedAt(@Param("senderId") Integer senderId, @Param("status") String status, Pageable p);

    @Query("select s.payloadId from SenderQueueEntry s where s.senderId = :senderId and s.payloadId in :payloads")
    List<String> findPayloadIdsBySenderIdAndPayloadIdIn(@Param("senderId") Integer senderId, @Param("payloads") List<String> payloads);
}
