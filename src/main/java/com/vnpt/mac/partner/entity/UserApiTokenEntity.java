package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_api_tokens")
public class UserApiTokenEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String name;
    @Column(name = "token_prefix", nullable = false) private String tokenPrefix;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(nullable = false) private String scopes;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected UserApiTokenEntity() {}
    public static UserApiTokenEntity create(UUID userId, String name, String prefix, String hash, String scopes, Instant expiresAt) {
        var t = new UserApiTokenEntity(); t.id = UUID.randomUUID(); t.userId = userId; t.name = name;
        t.tokenPrefix = prefix; t.tokenHash = hash; t.scopes = scopes; t.expiresAt = expiresAt; t.createdAt = Instant.now(); return t;
    }
    public boolean usable() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
    public void revoke() { revokedAt = Instant.now(); }
    public void used() { lastUsedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getTokenPrefix() { return tokenPrefix; }
    public String getTokenHash() { return tokenHash; }
    public String getScopes() { return scopes; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
