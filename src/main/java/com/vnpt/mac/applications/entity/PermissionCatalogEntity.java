package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permission_catalog")
public class PermissionCatalogEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionSensitivity sensitivity;

    @Column(name = "requires_manual_review", nullable = false)
    private boolean requiresManualReview;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    protected PermissionCatalogEntity() {}

    public static PermissionCatalogEntity create(String code, String displayName, PermissionSensitivity sensitivity,
                                                  boolean requiresManualReview) {
        var entity = new PermissionCatalogEntity();
        entity.id = UUID.randomUUID();
        entity.code = code.trim();
        entity.displayName = displayName.trim();
        entity.sensitivity = sensitivity;
        entity.requiresManualReview = requiresManualReview;
        entity.isActive = true;
        return entity;
    }

    public void update(String displayName, PermissionSensitivity sensitivity, boolean requiresManualReview, boolean isActive) {
        this.displayName = displayName.trim();
        this.sensitivity = sensitivity;
        this.requiresManualReview = requiresManualReview;
        this.isActive = isActive;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public PermissionSensitivity getSensitivity() { return sensitivity; }
    public boolean isRequiresManualReview() { return requiresManualReview; }
    public boolean isActive() { return isActive; }
}
