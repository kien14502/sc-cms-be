package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_developer_assignments")
public class AppDeveloperAssignmentEntity {
    @Id
    private UUID id;
    @Column(name = "app_id", nullable = false)
    private UUID appId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AppDeveloperAssignmentEntity() {
    }

    public static AppDeveloperAssignmentEntity grant(UUID appId, UUID userId, UUID grantedBy) {
        var a = new AppDeveloperAssignmentEntity();
        a.id = UUID.randomUUID();
        a.appId = appId;
        a.userId = userId;
        a.grantedBy = grantedBy;
        a.grantedAt = Instant.now();
        return a;
    }

    public void revoke() {
        revokedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }
}
