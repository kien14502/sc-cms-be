package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.dto.VersionDtos.VersionMetadataFields;
import com.vnpt.mac.applications.dto.VersionDtos.VersionResponse;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record CreateApplicationRequest(@NotNull ApplicationType appType,
                                           @NotNull @Valid VersionMetadataFields version) {
    }

    public record ApplicationResponse(UUID id, String appCode, ApplicationType appType, ApplicationStatus status,
                                      boolean firstParty, boolean killSwitchActive, UUID partnerId,
                                      long versionCount, VersionResponse latestVersion, long revision) {
        public static ApplicationResponse from(ApplicationEntity a, long versionCount, VersionResponse latestVersion) {
            return new ApplicationResponse(a.getId(), a.getAppCode(), a.getAppType(), a.getStatus(),
                    a.isFirstParty(), a.isKillSwitchActive(), a.getPartnerId(), versionCount, latestVersion, a.getRevision());
        }
    }
}
