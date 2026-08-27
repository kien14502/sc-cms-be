package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_version_capabilities")
public class AppVersionCapabilityEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "capability_id", nullable = false)
    private UUID capabilityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapabilityRequestStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AppVersionCapabilityEntity() {}

    public static AppVersionCapabilityEntity request(UUID versionId, UUID capabilityId, CapabilityRequestStatus initialStatus) {
        var entity = new AppVersionCapabilityEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.capabilityId = capabilityId;
        entity.status = initialStatus;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void approve(UUID decidedBy) {
        requireStatus(CapabilityRequestStatus.PENDING_REVIEW);
        status = CapabilityRequestStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID decidedBy, String reason) {
        requireStatus(CapabilityRequestStatus.PENDING_REVIEW);
        status = CapabilityRequestStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    private void requireStatus(CapabilityRequestStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.CAPABILITY_NOT_PENDING_REVIEW,
                "Capability request đang ở trạng thái " + status);
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public UUID getCapabilityId() { return capabilityId; }
    public CapabilityRequestStatus getStatus() { return status; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
