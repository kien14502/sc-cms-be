package com.vnpt.mac.partner.dto;

import com.vnpt.mac.partner.entity.PartnerEntity;
import com.vnpt.mac.partner.entity.PartnerQuotaEntity;
import com.vnpt.mac.partner.entity.PartnerStatus;
import com.vnpt.mac.partner.entity.PartnerStatusHistoryEntity;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class PartnerDtos {
    private PartnerDtos() {
    }

    public record CreatePartnerRequest(@NotBlank @Size(max = 255) String name,
                                       @Size(max = 50) String taxCode,
                                       @NotBlank @Email String contactEmail,
                                       @Size(max = 30) String contactPhone,
                                       boolean activateImmediately) {
    }

    public record UpdatePartnerRequest(@NotBlank @Size(max = 255) String name,
                                       @NotBlank @Email String contactEmail,
                                       @Size(max = 30) String contactPhone) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 5, max = 1000) String reason) {
    }

    public record PartnerResponse(UUID id, String partnerCode, String name, String taxCode,
                                  String contactEmail, String contactPhone, PartnerStatus status,
                                  String suspendReason, Instant suspendedAt, long revision) {
        public static PartnerResponse from(PartnerEntity p) {
            return new PartnerResponse(p.getId(), p.getPartnerCode(), p.getName(), p.getTaxCode(),
                    p.getContactEmail(), p.getContactPhone(), p.getStatus(), p.getSuspendReason(), p.getSuspendedAt(), p.getRevision());
        }
    }

    public record UpdateQuotaRequest(@PositiveOrZero int maxApps, @PositiveOrZero int maxDevelopers,
                                     @PositiveOrZero int maxConcurrentSubmissions,
                                     @PositiveOrZero long maxStorageBytes) {
    }

    public record QuotaResponse(UUID partnerId, int maxApps, int maxDevelopers,
                                int maxConcurrentSubmissions, long maxStorageBytes, long developerUsage,
                                long appUsage) {
        public static QuotaResponse from(PartnerQuotaEntity q, long developers, long apps) {
            return new QuotaResponse(q.getPartnerId(), q.getMaxApps(), q.getMaxDevelopers(),
                    q.getMaxConcurrentSubmissions(), q.getMaxStorageBytes(), developers, apps);
        }
    }

    public record StatusHistoryResponse(UUID id, PartnerStatus fromStatus, PartnerStatus toStatus,
                                        String reason, UUID changedBy, Instant changedAt) {
        public static StatusHistoryResponse from(PartnerStatusHistoryEntity history) {
            return new StatusHistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                    history.getReason(), history.getChangedBy(), history.getChangedAt());
        }
    }
}
