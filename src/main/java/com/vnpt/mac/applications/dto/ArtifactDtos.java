package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArtifactDtos {
    private ArtifactDtos() {}

    public record ArtifactResponse(UUID id, UUID versionId, ArtifactKind kind, String originalFilename,
                                   long sizeBytes, String checksumSha256, String signatureFingerprint, Instant createdAt) {
        public static ArtifactResponse from(VersionArtifactEntity e) {
            return new ArtifactResponse(e.getId(), e.getVersionId(), e.getKind(), e.getOriginalFilename(),
                    e.getSizeBytes(), e.getChecksumSha256(), e.getSignatureFingerprint(), e.getCreatedAt());
        }
    }

    public record WebappConfigRequest(@NotBlank @Size(max = 500) String destinationUrl) {
    }

    public record WebappConfigResponse(UUID versionId, String destinationUrl, boolean sslValid,
                                       Integer lastHealthStatus, Instant lastCheckedAt) {
        public static WebappConfigResponse from(VersionWebappConfigEntity e) {
            return new WebappConfigResponse(e.getVersionId(), e.getDestinationUrl(), e.isSslValid(),
                    e.getLastHealthStatus(), e.getLastCheckedAt());
        }
    }

    public record ModuleConfigRequest(@NotBlank @Size(max = 255) String moduleNamespace, String description) {
    }

    public record ModuleConfigResponse(UUID versionId, String moduleNamespace, String description) {
        public static ModuleConfigResponse from(VersionModuleConfigEntity e) {
            return new ModuleConfigResponse(e.getVersionId(), e.getModuleNamespace(), e.getDescription());
        }
    }

    public record FindingResponse(UUID id, String ruleCode, FindingSeverity severity, String message, Map<String, Object> context) {
        public static FindingResponse from(ValidationFindingEntity e) {
            return new FindingResponse(e.getId(), e.getRuleCode(), e.getSeverity(), e.getMessage(), e.getContext());
        }
    }

    public record ValidationRunResponse(UUID id, UUID versionId, ValidationStatus status, Instant startedAt,
                                        Instant completedAt, List<FindingResponse> findings) {
        public static ValidationRunResponse from(ValidationRunEntity run, List<ValidationFindingEntity> findings) {
            return new ValidationRunResponse(run.getId(), run.getVersionId(), run.getStatus(), run.getStartedAt(),
                    run.getCompletedAt(), findings.stream().map(FindingResponse::from).toList());
        }

        public static ValidationRunResponse empty(UUID versionId) {
            return new ValidationRunResponse(null, versionId, null, null, null, List.of());
        }
    }
}
