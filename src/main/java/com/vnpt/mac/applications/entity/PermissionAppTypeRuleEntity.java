package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permission_app_type_rules")
public class PermissionAppTypeRuleEntity {
    @Id
    private UUID id;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 20)
    private ApplicationType appType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleEffect effect;

    @Column(length = 500)
    private String reason;

    protected PermissionAppTypeRuleEntity() {}

    public static PermissionAppTypeRuleEntity create(UUID permissionId, ApplicationType appType, RuleEffect effect, String reason) {
        var entity = new PermissionAppTypeRuleEntity();
        entity.id = UUID.randomUUID();
        entity.permissionId = permissionId;
        entity.appType = appType;
        entity.effect = effect;
        entity.reason = reason;
        return entity;
    }

    public void update(RuleEffect effect, String reason) {
        this.effect = effect;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getPermissionId() { return permissionId; }
    public ApplicationType getAppType() { return appType; }
    public RuleEffect getEffect() { return effect; }
    public String getReason() { return reason; }
}
