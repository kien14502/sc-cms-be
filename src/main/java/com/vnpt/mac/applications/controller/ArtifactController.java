package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ArtifactDtos.*;
import com.vnpt.mac.applications.service.ArtifactService;
import com.vnpt.mac.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions/{versionId}")
@Tag(name = "Artifact", description = "Upload artifact/cấu hình theo app type và validate manifest")
public class ArtifactController {
    private final ArtifactService service;

    public ArtifactController(ArtifactService service) {
        this.service = service;
    }

    @PostMapping(value = "/artifact", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Upload artifact (ZIP/APK/AAB)", description = "MiniApp nhận ZIP, Feature App nhận APK/AAB. Chạy validate ngay sau khi lưu.")
    public ApiResponse<ArtifactResponse> upload(@PathVariable UUID appId, @PathVariable UUID versionId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(service.uploadArtifact(appId, versionId, file));
    }

    @PutMapping("/webapp-config")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cấu hình WebApp destination URL")
    public ApiResponse<WebappConfigResponse> webappConfig(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody WebappConfigRequest r) {
        return ApiResponse.success(service.setWebappConfig(appId, versionId, r));
    }

    @PutMapping("/module-config")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cấu hình App Module metadata")
    public ApiResponse<ModuleConfigResponse> moduleConfig(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody ModuleConfigRequest r) {
        return ApiResponse.success(service.setModuleConfig(appId, versionId, r));
    }

    @GetMapping("/validation")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Kết quả validate mới nhất")
    public ApiResponse<ValidationRunResponse> validation(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.latestValidation(appId, versionId));
    }
}
