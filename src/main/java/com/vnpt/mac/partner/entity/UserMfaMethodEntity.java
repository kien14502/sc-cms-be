package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_methods")
public class UserMfaMethodEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false) private MfaMethodType methodType;
    @Column(name = "secret_value", nullable = false) private String secretValue;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "disabled_at") private Instant disabledAt;
    protected UserMfaMethodEntity() {}
    public static UserMfaMethodEntity pending(UUID userId, String secret) {
        var m = new UserMfaMethodEntity(); m.id = UUID.randomUUID(); m.userId = userId;
        m.methodType = MfaMethodType.TOTP; m.secretValue = secret; return m;
    }
    public void verify() { verifiedAt = Instant.now(); disabledAt = null; }
    public void disable() { disabledAt = Instant.now(); }
    public boolean active() { return verifiedAt != null && disabledAt == null; }
    public String getSecretValue() { return secretValue; }
    public UUID getUserId() { return userId; }
}
