package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.Instant;
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

    @Column(name = "is_first_party", nullable = false)
    private boolean firstParty;

    @Column(name = "kill_switch_active", nullable = false)
    private boolean killSwitchActive;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ApplicationEntity() {}

    public static ApplicationEntity create(UUID partnerId, ApplicationType appType) {
        var entity = new ApplicationEntity();
        entity.id = UUID.randomUUID();
        entity.partnerId = partnerId;
        entity.appCode = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.appType = appType;
        entity.status = ApplicationStatus.DRAFT;
        entity.firstParty = false;
        entity.killSwitchActive = false;
        return entity;
    }

    public void activate() {
        if (status == ApplicationStatus.DRAFT) status = ApplicationStatus.ACTIVE;
    }

    public void archive() {
        status = ApplicationStatus.ARCHIVED;
    }

    public UUID getId() { return id; }
    public UUID getPartnerId() { return partnerId; }
    public String getAppCode() { return appCode; }
    public ApplicationType getAppType() { return appType; }
    public ApplicationStatus getStatus() { return status; }
    public boolean isFirstParty() { return firstParty; }
    public boolean isKillSwitchActive() { return killSwitchActive; }
    public Instant getDeletedAt() { return deletedAt; }
}
