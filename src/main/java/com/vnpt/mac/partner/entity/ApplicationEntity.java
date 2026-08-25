package com.vnpt.mac.partner.entity;

import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class ApplicationEntity extends BaseAuditEntity {
    @Id
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "app_code", nullable = false, unique = true, length = 50)
    private String appCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 30)
    private ApplicationType appType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    protected ApplicationEntity() {}

    public static ApplicationEntity create(UUID partnerId, String appCode, ApplicationType appType) {
        var entity = new ApplicationEntity();
        entity.id = UUID.randomUUID();
        entity.partnerId = partnerId;
        entity.appCode = appCode.trim();
        entity.appType = appType;
        entity.status = ApplicationStatus.DRAFT;
        return entity;
    }

    public void archive() {
        status = ApplicationStatus.ARCHIVED;
    }

    public UUID getId() { return id; }
    public UUID getPartnerId() { return partnerId; }
    public String getAppCode() { return appCode; }
    public ApplicationType getAppType() { return appType; }
    public ApplicationStatus getStatus() { return status; }
}
