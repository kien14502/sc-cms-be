package com.vnpt.mac.partner.dto;

import com.vnpt.mac.partner.entity.RoleCode;
import com.vnpt.mac.partner.entity.UserApiTokenEntity;
import com.vnpt.mac.partner.entity.UserEntity;
import com.vnpt.mac.partner.entity.UserStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {
    }

    public record InviteUserRequest(@NotBlank @Email String email, @NotBlank @Size(max = 255) String fullName,
                                    @NotNull RoleCode role) {
    }

    public record InvitationResponse(UUID userId, String invitationToken, Instant expiresAt) {
    }

    public record AcceptInvitationRequest(@NotBlank String token,
                                          @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record UserResponse(UUID id, UUID partnerId, String email, String fullName, String publicEmail,
                               String bio, UserStatus status, boolean mfaEnabled, Set<RoleCode> roles, long revision) {
        public static UserResponse from(UserEntity u) {
            return new UserResponse(u.getId(), u.getPartnerId(), u.getEmail(), u.getFullName(), u.getPublicEmail(),
                    u.getBio(), u.getStatus(), u.isMfaEnabled(), u.getRoles().stream().map(r -> r.getCode()).collect(java.util.stream.Collectors.toSet()), u.getRevision());
        }
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName, @Email String publicEmail,
                                       @Size(max = 1000) String bio) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record CreateAdminRequest(@NotBlank @Email String email, @NotBlank String fullName, @NotNull RoleCode role) {
    }

    public record AssignDevelopersRequest(@NotEmpty List<UUID> developerIds) {
    }

    public record CreateApiTokenRequest(@NotBlank @Size(max = 100) String name, @NotEmpty Set<String> scopes,
                                        @Min(1) @Max(365) Integer expiresInDays) {
    }

    public record CreatedApiTokenResponse(UUID id, String token, String prefix, Set<String> scopes, Instant expiresAt) {
    }

    public record ApiTokenResponse(UUID id, String name, String prefix, Set<String> scopes, Instant expiresAt,
                                   Instant lastUsedAt, Instant revokedAt, Instant createdAt) {
        public static ApiTokenResponse from(UserApiTokenEntity t) {
            return new ApiTokenResponse(t.getId(), t.getName(), t.getTokenPrefix(), java.util.Arrays.stream(t.getScopes().split(","))
                    .filter(scope -> !scope.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    t.getExpiresAt(), t.getLastUsedAt(), t.getRevokedAt(), t.getCreatedAt());
        }
    }
}
