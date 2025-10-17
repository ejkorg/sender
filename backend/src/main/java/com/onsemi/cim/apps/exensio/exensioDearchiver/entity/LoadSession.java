package com.onsemi.cim.apps.exensio.exensioDearchiver.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "load_session")
public class LoadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String initiatedBy;
    private String site;
    private String environment;
    private Integer senderId;
    private String source;

    private String status; // CREATED, DISCOVERING, ENQUEUED_LOCAL, PUSHING_REMOTE, COMPLETED, FAILED

    private Integer totalPayloads = 0;
    private Integer enqueuedLocalCount = 0;
    private Integer pushedRemoteCount = 0;
    private Integer skippedCount = 0;
    private Integer failedCount = 0;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public LoadSession() {}

    public LoadSession(String initiatedBy, String site, String environment, Integer senderId, String source) {
        this.initiatedBy = initiatedBy;
        this.site = site;
        this.environment = environment;
        this.senderId = senderId;
        this.source = source;
        this.status = "CREATED";
    }

    @PrePersist
    void prePersistDefaults() {
        if (this.initiatedBy == null || this.initiatedBy.isBlank()) {
            this.initiatedBy = "ui";
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "CREATED";
        }
        if (this.createdAt == null) {
            this.createdAt = java.time.Instant.now();
        }
        this.updatedAt = java.time.Instant.now();
    }

    // Getters and setters
    // ...generated getters/setters omitted for brevity in patch; they'll be present in file
    public Long getId() { return id; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalPayloads() { return totalPayloads; }
    public void setTotalPayloads(Integer totalPayloads) { this.totalPayloads = totalPayloads; }
    public Integer getEnqueuedLocalCount() { return enqueuedLocalCount; }
    public void setEnqueuedLocalCount(Integer enqueuedLocalCount) { this.enqueuedLocalCount = enqueuedLocalCount; }
    public Integer getPushedRemoteCount() { return pushedRemoteCount; }
    public void setPushedRemoteCount(Integer pushedRemoteCount) { this.pushedRemoteCount = pushedRemoteCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
