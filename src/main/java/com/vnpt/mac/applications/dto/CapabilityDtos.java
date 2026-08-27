package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CapabilityDtos {
    private CapabilityDtos() {}

    public record CapabilityCatalogResponse(UUID id, String code, String displayName,
                                             List<ApplicationType> allowedAppTypes, boolean isActive) {
        public static CapabilityCatalogResponse from(CapabilityCatalogEntity e) {
            return new CapabilityCatalogResponse(e.getId(), e.getCode(), e.getDisplayName(), e.getAllowedAppTypes(), e.isActive());
        }
    }

    public record CreateCapabilityCatalogRequest(@NotBlank @Size(max = 50) String code,
                                                  @NotBlank @Size(max = 255) String displayName,
                                                  @NotEmpty List<ApplicationType> allowedAppTypes) {
    }

    public record UpdateCapabilityCatalogRequest(@NotBlank @Size(max = 255) String displayName,
                                                  @NotEmpty List<ApplicationType> allowedAppTypes,
                                                  boolean isActive) {
    }

    public record RequestCapabilityRequest(@NotBlank String capabilityCode) {
    }

    public record DecideCapabilityRequest(@NotNull CapabilityDecisionType decision, @Size(max = 500) String reason) {
    }

    public enum CapabilityDecisionType { APPROVE, REJECT }

    public record AppVersionCapabilityResponse(UUID id, UUID versionId, String capabilityCode, String displayName,
                                                CapabilityRequestStatus status, UUID decidedBy, String decisionReason,
                                                Instant createdAt, Instant decidedAt) {
        public static AppVersionCapabilityResponse from(AppVersionCapabilityEntity e, CapabilityCatalogEntity catalog) {
            return new AppVersionCapabilityResponse(e.getId(), e.getVersionId(), catalog.getCode(), catalog.getDisplayName(),
                    e.getStatus(), e.getDecidedBy(), e.getDecisionReason(), e.getCreatedAt(), e.getDecidedAt());
        }
    }
}
