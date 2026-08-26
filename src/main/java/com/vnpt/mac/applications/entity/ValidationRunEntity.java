package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_runs")
public class ValidationRunEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus status;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ValidationRunEntity() {}

    public static ValidationRunEntity start(UUID versionId, UUID triggeredBy) {
        var entity = new ValidationRunEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.status = ValidationStatus.RUNNING;
        entity.triggeredBy = triggeredBy;
        entity.startedAt = Instant.now();
        return entity;
    }

    public void complete(boolean passed) {
        status = passed ? ValidationStatus.PASSED : ValidationStatus.FAILED;
        completedAt = Instant.now();
    }

    public boolean passed() { return status == ValidationStatus.PASSED; }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public ValidationStatus getStatus() { return status; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
