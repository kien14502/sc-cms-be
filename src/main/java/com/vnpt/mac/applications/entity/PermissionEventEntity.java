package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission_events")
public class PermissionEventEntity {
    @Id
    private UUID id;

    @Column(name = "app_version_permission_id", nullable = false)
    private UUID appVersionPermissionId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PermissionEventEntity() {}

    public static PermissionEventEntity create(UUID appVersionPermissionId, String eventType, UUID actorId, String note) {
        var entity = new PermissionEventEntity();
        entity.id = UUID.randomUUID();
        entity.appVersionPermissionId = appVersionPermissionId;
        entity.eventType = eventType;
        entity.actorId = actorId;
        entity.note = note;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getAppVersionPermissionId() { return appVersionPermissionId; }
    public String getEventType() { return eventType; }
    public UUID getActorId() { return actorId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
