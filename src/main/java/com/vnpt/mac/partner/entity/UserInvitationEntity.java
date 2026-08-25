package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
public class UserInvitationEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserInvitationEntity() {}
    public static UserInvitationEntity create(UUID userId, String tokenHash, Instant expiresAt) {
        var i = new UserInvitationEntity();
        i.id = UUID.randomUUID(); i.userId = userId; i.tokenHash = tokenHash;
        i.expiresAt = expiresAt; i.createdAt = Instant.now(); return i;
    }
    public boolean usable() { return consumedAt == null && expiresAt.isAfter(Instant.now()); }
    public void consume() { consumedAt = Instant.now(); }
    public UUID getUserId() { return userId; }
}
