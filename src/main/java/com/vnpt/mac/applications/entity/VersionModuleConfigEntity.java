package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "version_module_config")
public class VersionModuleConfigEntity {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "module_namespace", nullable = false)
    private String moduleNamespace;

    @Column
    private String description;

    protected VersionModuleConfigEntity() {}

    public static VersionModuleConfigEntity create(UUID versionId, String moduleNamespace, String description) {
        var entity = new VersionModuleConfigEntity();
        entity.versionId = versionId;
        entity.moduleNamespace = moduleNamespace;
        entity.description = description;
        return entity;
    }

    public void update(String moduleNamespace, String description) {
        this.moduleNamespace = moduleNamespace;
        this.description = description;
    }

    public UUID getVersionId() { return versionId; }
    public String getModuleNamespace() { return moduleNamespace; }
    public String getDescription() { return description; }
}
