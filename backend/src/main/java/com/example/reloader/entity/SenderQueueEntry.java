package com.example.reloader.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sender_queue")
public class SenderQueueEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload_id", nullable = false, length = 1000)
    private String payloadId;

    @Column(name = "sender_id")
    private Integer senderId;

    @Column(name = "status", length = 50)
    private String status; // NEW, QUEUED, PROCESSING, SENT, FAILED

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "source", length = 200)
    private String source;

    public SenderQueueEntry() {}

    public SenderQueueEntry(Integer senderId, String payloadId, String source) {
        this.senderId = senderId;
        this.payloadId = payloadId;
        this.source = source;
        this.status = "NEW";
        this.createdAt = Instant.now();
    }

    // getters/setters omitted for brevity (IDE will generate)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPayloadId() { return payloadId; }
    public void setPayloadId(String payloadId) { this.payloadId = payloadId; }
    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
