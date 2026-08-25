package com.vnpt.mac.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id private UUID id;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "actor_email") private String actorEmail;
    @Column(name = "actor_roles") private String actorRoles;
    @Column(name = "partner_id") private UUID partnerId;
    @Column(nullable = false) private String action;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "before_state", columnDefinition = "text") private String beforeState;
    @Column(name = "after_state", columnDefinition = "text") private String afterState;
    @Column(name = "correlation_id", nullable = false) private String correlationId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AuditLogEntity() {}
    public static AuditLogEntity create(UUID actorId, String email, String roles, UUID partnerId, String action,
                                        String resourceType, UUID resourceId, String ip, String agent,
                                        String beforeState, String afterState, String correlationId) {
        var e = new AuditLogEntity(); e.id = UUID.randomUUID(); e.actorUserId = actorId; e.actorEmail = email;
        e.actorRoles = roles; e.partnerId = partnerId; e.action = action; e.resourceType = resourceType;
        e.resourceId = resourceId; e.ipAddress = ip; e.userAgent = agent; e.beforeState = beforeState;
        e.afterState = afterState; e.correlationId = correlationId; e.createdAt = Instant.now(); return e;
    }
    public UUID getId(){return id;} public UUID getActorUserId(){return actorUserId;} public String getActorEmail(){return actorEmail;}
    public String getActorRoles(){return actorRoles;} public UUID getPartnerId(){return partnerId;} public String getAction(){return action;}
    public String getResourceType(){return resourceType;} public UUID getResourceId(){return resourceId;} public String getIpAddress(){return ipAddress;}
    public String getUserAgent(){return userAgent;} public String getBeforeState(){return beforeState;} public String getAfterState(){return afterState;}
    public String getCorrelationId(){return correlationId;} public Instant getCreatedAt(){return createdAt;}
}
