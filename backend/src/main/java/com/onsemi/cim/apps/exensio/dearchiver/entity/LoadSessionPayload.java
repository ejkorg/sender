package com.onsemi.cim.apps.exensio.dearchiver.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "load_session_payload", uniqueConstraints = @UniqueConstraint(name = "uk_load_session_payload_session_payload", columnNames = {"session_id", "payload_id"}))
public class LoadSessionPayload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private LoadSession session;

    private String payloadId; // e.g. "metadataId,idData"
    private String status = "NEW"; // NEW, ENQUEUED_LOCAL, PUSHED_REMOTE, SKIPPED, FAILED
    private String error;
    private String externalId;
    private Instant pushedAt;
    private Instant nextAttemptAt;
    private int attempts = 0;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    // Placeholder for future global payload mapping. Nullable; populated later when available.
    @Column(name = "global_payload_id")
    private Long globalPayloadId;

    public LoadSessionPayload() {}

    public LoadSessionPayload(LoadSession session, String payloadId) {
        this.session = session;
        this.payloadId = payloadId;
    }

    // Getters and setters
    public Long getId() { return id; }
    public LoadSession getSession() { return session; }
    public void setSession(LoadSession session) { this.session = session; }
    public String getPayloadId() { return payloadId; }
    public void setPayloadId(String payloadId) { this.payloadId = payloadId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Instant getPushedAt() { return pushedAt; }
    public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getGlobalPayloadId() { return globalPayloadId; }
    public void setGlobalPayloadId(Long globalPayloadId) { this.globalPayloadId = globalPayloadId; }

    // Convenience state transition helpers used by SessionPushService and tests
    public void markStaged() {
        this.status = "STAGED";
        this.updatedAt = Instant.now();
    }

    public void markPushed(String externalId) {
        this.status = "PUSHED";
        this.externalId = externalId;
        this.pushedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.error = error;
        this.updatedAt = Instant.now();
    }
}
