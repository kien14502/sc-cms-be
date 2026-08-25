package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.VersionDtos.*;
import com.vnpt.mac.applications.entity.VersionStatus;
import com.vnpt.mac.applications.service.VersionService;
import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions")
@Tag(name = "Version", description = "Quản lý version của 1 application")
public class VersionController {
    private final VersionService service;

    public VersionController(VersionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Danh sách version", description = "Lọc theo status. Cần quyền version.read.")
    public ApiResponse<PageResponse<VersionResponse>> list(@PathVariable UUID appId,
                                                            @RequestParam(required = false) VersionStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.listVersions(appId, status, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('version.create') and @resourceAuth.app(#appId)")
    @Operation(summary = "Tạo version mới", description = "version_code tự tăng. Cần quyền version.create.")
    public ApiResponse<VersionResponse> create(@PathVariable UUID appId, @Valid @RequestBody VersionMetadataFields r) {
        return ApiResponse.success(service.createVersion(appId, r));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Chi tiết version")
    public ApiResponse<VersionResponse> get(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.getVersion(appId, versionId));
    }

    @PatchMapping("/{versionId}")
    @PreAuthorize("hasAuthority('version.update') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cập nhật metadata version", description = "Chỉ cho phép khi version ở DRAFT hoặc CHANGES_REQUESTED.")
    public ApiResponse<VersionResponse> update(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody UpdateVersionRequest r) {
        return ApiResponse.success(service.updateVersion(appId, versionId, r));
    }
}
