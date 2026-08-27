package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_version_permissions")
public class AppVersionPermissionEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(nullable = false, columnDefinition = "text")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_sensitivity", nullable = false, length = 20)
    private PermissionSensitivity resolvedSensitivity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionRequestStatus status;

    @Column(name = "is_escalation", nullable = false)
    private boolean isEscalation;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected AppVersionPermissionEntity() {}

    public static AppVersionPermissionEntity request(UUID versionId, UUID permissionId, String justification,
                                                       PermissionSensitivity resolvedSensitivity,
                                                       PermissionRequestStatus initialStatus, boolean isEscalation) {
        var entity = new AppVersionPermissionEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.permissionId = permissionId;
        entity.justification = justification.trim();
        entity.resolvedSensitivity = resolvedSensitivity;
        entity.status = initialStatus;
        entity.isEscalation = isEscalation;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void approve(UUID decidedBy) {
        requireStatus(PermissionRequestStatus.PENDING_REVIEW);
        status = PermissionRequestStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID decidedBy, String reason) {
        requireStatus(PermissionRequestStatus.PENDING_REVIEW);
        status = PermissionRequestStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    private void requireStatus(PermissionRequestStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.PERMISSION_NOT_PENDING_REVIEW,
                "Permission request đang ở trạng thái " + status);
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public UUID getPermissionId() { return permissionId; }
    public String getJustification() { return justification; }
    public PermissionSensitivity getResolvedSensitivity() { return resolvedSensitivity; }
    public PermissionRequestStatus getStatus() { return status; }
    public boolean isEscalation() { return isEscalation; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
