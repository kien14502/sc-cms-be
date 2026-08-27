package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "capability_catalog")
public class CapabilityCatalogEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_app_types", nullable = false, columnDefinition = "jsonb")
    private List<ApplicationType> allowedAppTypes = List.of();

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    protected CapabilityCatalogEntity() {}

    public static CapabilityCatalogEntity create(String code, String displayName, List<ApplicationType> allowedAppTypes) {
        var entity = new CapabilityCatalogEntity();
        entity.id = UUID.randomUUID();
        entity.code = code.trim();
        entity.displayName = displayName.trim();
        entity.allowedAppTypes = allowedAppTypes == null ? List.of() : allowedAppTypes;
        entity.isActive = true;
        return entity;
    }

    public void update(String displayName, List<ApplicationType> allowedAppTypes, boolean isActive) {
        this.displayName = displayName.trim();
        this.allowedAppTypes = allowedAppTypes == null ? List.of() : allowedAppTypes;
        this.isActive = isActive;
    }

    public boolean allowsAppType(ApplicationType appType) {
        return allowedAppTypes.contains(appType);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public List<ApplicationType> getAllowedAppTypes() { return allowedAppTypes; }
    public boolean isActive() { return isActive; }
}
