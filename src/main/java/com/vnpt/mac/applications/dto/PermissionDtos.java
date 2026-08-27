package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PermissionDtos {
    private PermissionDtos() {}

    public record PermissionCatalogResponse(UUID id, String code, String displayName,
                                            PermissionSensitivity sensitivity, boolean requiresManualReview, boolean isActive) {
        public static PermissionCatalogResponse from(PermissionCatalogEntity e) {
            return new PermissionCatalogResponse(e.getId(), e.getCode(), e.getDisplayName(),
                    e.getSensitivity(), e.isRequiresManualReview(), e.isActive());
        }
    }

    public record CreatePermissionCatalogRequest(@NotBlank @Size(max = 50) String code,
                                                  @NotBlank @Size(max = 255) String displayName,
                                                  @NotNull PermissionSensitivity sensitivity,
                                                  boolean requiresManualReview) {
    }

    public record UpdatePermissionCatalogRequest(@NotBlank @Size(max = 255) String displayName,
                                                  @NotNull PermissionSensitivity sensitivity,
                                                  boolean requiresManualReview,
                                                  boolean isActive) {
    }

    public record UpsertAppTypeRuleRequest(@NotNull RuleEffect effect, @Size(max = 500) String reason) {
    }

    public record AppTypeRuleResponse(UUID id, UUID permissionId, ApplicationType appType, RuleEffect effect, String reason) {
        public static AppTypeRuleResponse from(PermissionAppTypeRuleEntity e) {
            return new AppTypeRuleResponse(e.getId(), e.getPermissionId(), e.getAppType(), e.getEffect(), e.getReason());
        }
    }

    public record RequestPermissionRequest(@NotBlank String permissionCode,
                                           @NotBlank @Size(min = 20, max = 1000) String justification) {
    }

    public record DecidePermissionRequest(@NotNull PermissionDecisionType decision,
                                          @Size(max = 500) String reason) {
    }

    public enum PermissionDecisionType { APPROVE, REJECT }

    public record AppVersionPermissionResponse(UUID id, UUID versionId, String permissionCode, String displayName,
                                                String justification, PermissionSensitivity resolvedSensitivity,
                                                PermissionRequestStatus status, boolean isEscalation,
                                                UUID decidedBy, String decisionReason, Instant createdAt, Instant decidedAt) {
        public static AppVersionPermissionResponse from(AppVersionPermissionEntity e, PermissionCatalogEntity catalog) {
            return new AppVersionPermissionResponse(e.getId(), e.getVersionId(), catalog.getCode(), catalog.getDisplayName(),
                    e.getJustification(), e.getResolvedSensitivity(), e.getStatus(), e.isEscalation(),
                    e.getDecidedBy(), e.getDecisionReason(), e.getCreatedAt(), e.getDecidedAt());
        }
    }

    public record PermissionEventResponse(UUID id, String eventType, UUID actorId, String note, Instant createdAt) {
        public static PermissionEventResponse from(PermissionEventEntity e) {
            return new PermissionEventResponse(e.getId(), e.getEventType(), e.getActorId(), e.getNote(), e.getCreatedAt());
        }
    }
}
