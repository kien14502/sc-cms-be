package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class PermissionEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    @Column(nullable = false) private String description;
    protected PermissionEntity() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
}
