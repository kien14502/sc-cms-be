package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_categories")
public class AppCategoryEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    protected AppCategoryEntity() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
