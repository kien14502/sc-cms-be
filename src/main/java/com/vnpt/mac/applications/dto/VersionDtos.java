package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VersionDtos {
    private VersionDtos() {}

    public record VersionMetadataFields(@NotBlank @Size(max = 50) String versionName,
                                        @NotBlank @Size(max = 255) String displayName,
                                        @NotBlank @Size(max = 255) String packageName,
                                        @Size(max = 500) String descriptionShort,
                                        String descriptionLong,
                                        List<String> supportedLanguages,
                                        Set<String> categoryCodes) {
    }

    public record UpdateVersionRequest(@NotBlank @Size(max = 255) String displayName,
                                       @Size(max = 500) String descriptionShort,
                                       String descriptionLong,
                                       List<String> supportedLanguages,
                                       Set<String> categoryCodes) {
    }

    public record VersionResponse(UUID id, UUID appId, int versionCode, String versionName, VersionStatus status,
                                  String displayName, String packageName, String descriptionShort, String descriptionLong,
                                  List<String> supportedLanguages, Set<String> categoryCodes, int reviewRound, long revision) {
        public static VersionResponse from(AppVersionEntity v) {
            return new VersionResponse(v.getId(), v.getAppId(), v.getVersionCode(), v.getVersionName(), v.getStatus(),
                    v.getDisplayName(), v.getPackageName(), v.getDescriptionShort(), v.getDescriptionLong(),
                    v.getSupportedLanguages(), v.getCategories().stream().map(c -> c.getCode()).collect(Collectors.toSet()),
                    v.getReviewRound(), v.getRevision());
        }
    }
}
