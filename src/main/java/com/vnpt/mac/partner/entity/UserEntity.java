package com.vnpt.mac.partner.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity extends BaseAuditEntity {
    @Id
    private UUID id;
    @Column(name = "partner_id")
    private UUID partnerId;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "public_email")
    private String publicEmail;
    @Column
    private String bio;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    protected UserEntity() {
    }

    public static UserEntity invited(UUID partnerId, String email, String fullName, RoleEntity role) {
        var user = new UserEntity();
        user.id = UUID.randomUUID();
        user.partnerId = partnerId;
        user.email = email.trim().toLowerCase();
        user.fullName = fullName.trim();
        user.status = UserStatus.INVITED;
        user.roles.add(role);
        return user;
    }

    public void activate(String passwordHash) {
        if (status != UserStatus.INVITED) throw new BusinessException(ErrorCode.USER_STATUS_INVALID);
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
    }

    public void updateProfile(String fullName, String publicEmail, String bio) {
        this.fullName = fullName.trim();
        this.publicEmail = publicEmail == null || publicEmail.isBlank() ? null : publicEmail.trim().toLowerCase();
        this.bio = bio == null || bio.isBlank() ? null : bio.trim();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void enableMfa() {
        this.mfaEnabled = true;
    }

    public void disableMfa() {
        this.mfaEnabled = false;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartnerId() {
        return partnerId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPublicEmail() {
        return publicEmail;
    }

    public String getBio() {
        return bio;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }
}
