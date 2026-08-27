package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.PermissionDtos.*;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.service.PermissionService;
import com.vnpt.mac.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Permission", description = "Permission catalog và khai báo/duyệt permission theo version")
public class PermissionController {
    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/permissions/catalog")
    @PreAuthorize("hasAuthority('permission.catalog.read')")
    @Operation(summary = "Danh mục permission", description = "Danh sách permission đang active. Cần quyền permission.catalog.read.")
    public ApiResponse<List<PermissionCatalogResponse>> catalog() {
        return ApiResponse.success(service.listCatalog());
    }

    @GetMapping("/api/v1/permissions/catalog/all")
    @PreAuthorize("hasAuthority('permission.catalog.manage')")
    @Operation(summary = "Danh mục permission (kể cả inactive)", description = "Dùng cho màn quản trị catalog. Cần quyền permission.catalog.manage.")
    public ApiResponse<List<PermissionCatalogResponse>> allCatalog() {
        return ApiResponse.success(service.listAllCatalog());
    }

    @PostMapping("/api/v1/permissions/catalog")
    @PreAuthorize("hasAuthority('permission.catalog.manage')")
    @Operation(summary = "Tạo permission catalog entry", description = "Cần quyền permission.catalog.manage (Platform Admin).")
    public ApiResponse<PermissionCatalogResponse> createCatalogEntry(@Valid @RequestBody CreatePermissionCatalogRequest r) {
        return ApiResponse.success(service.createCatalogEntry(r));
    }

    @PatchMapping("/api/v1/permissions/catalog/{permissionId}")
    @PreAuthorize("hasAuthority('permission.catalog.manage')")
    @Operation(summary = "Cập nhật permission catalog entry", description = "Cập nhật displayName/sensitivity/requiresManualReview/isActive. Cần quyền permission.catalog.manage.")
    public ApiResponse<PermissionCatalogResponse> updateCatalogEntry(@PathVariable UUID permissionId,
                                                                      @Valid @RequestBody UpdatePermissionCatalogRequest r) {
        return ApiResponse.success(service.updateCatalogEntry(permissionId, r));
    }

    @PutMapping("/api/v1/permissions/catalog/{permissionId}/rules/{appType}")
    @PreAuthorize("hasAuthority('permission.catalog.manage')")
    @Operation(summary = "Thiết lập rule theo app type", description = "Tạo mới hoặc cập nhật rule (ALLOW/DENY/CONDITIONAL) cho 1 (permission, appType). Cần quyền permission.catalog.manage.")
    public ApiResponse<AppTypeRuleResponse> upsertAppTypeRule(@PathVariable UUID permissionId, @PathVariable ApplicationType appType,
                                                               @Valid @RequestBody UpsertAppTypeRuleRequest r) {
        return ApiResponse.success(service.upsertAppTypeRule(permissionId, appType, r));
    }

    @DeleteMapping("/api/v1/permissions/catalog/{permissionId}/rules/{appType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('permission.catalog.manage')")
    @Operation(summary = "Gỡ rule theo app type", description = "Trả về mặc định ALLOW cho (permission, appType) này. Cần quyền permission.catalog.manage.")
    public void deleteAppTypeRule(@PathVariable UUID permissionId, @PathVariable ApplicationType appType) {
        service.deleteAppTypeRule(permissionId, appType);
    }

    @GetMapping("/api/v1/applications/{appId}/versions/{versionId}/permissions")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Danh sách permission đã khai báo trên version")
    public ApiResponse<List<AppVersionPermissionResponse>> list(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.listForVersion(appId, versionId));
    }

    @PostMapping("/api/v1/applications/{appId}/versions/{versionId}/permissions")
    @PreAuthorize("hasAuthority('permission.request') and @resourceAuth.app(#appId)")
    @Operation(summary = "Khai báo permission", description = "Chọn permission từ catalog + justification (>= 20 ký tự). Cần quyền permission.request.")
    public ApiResponse<AppVersionPermissionResponse> request(@PathVariable UUID appId, @PathVariable UUID versionId,
                                                              @Valid @RequestBody RequestPermissionRequest r) {
        return ApiResponse.success(service.requestPermission(appId, versionId, r));
    }

    @DeleteMapping("/api/v1/applications/{appId}/versions/{versionId}/permissions/{permissionRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('permission.request') and @resourceAuth.app(#appId)")
    @Operation(summary = "Gỡ permission đã khai báo", description = "Chỉ khi version ở DRAFT/CHANGES_REQUESTED.")
    public void remove(@PathVariable UUID appId, @PathVariable UUID versionId, @PathVariable UUID permissionRequestId) {
        service.remove(appId, versionId, permissionRequestId);
    }

    @PostMapping("/api/v1/applications/{appId}/versions/{versionId}/permissions/{permissionRequestId}/decide")
    @PreAuthorize("hasAuthority('permission.decide')")
    @Operation(summary = "Duyệt/Từ chối permission", description = "reason bắt buộc khi REJECT. Cần quyền permission.decide (Reviewer/Platform Admin, không scope theo partner).")
    public ApiResponse<AppVersionPermissionResponse> decide(@PathVariable UUID appId, @PathVariable UUID versionId,
                                                             @PathVariable UUID permissionRequestId,
                                                             @Valid @RequestBody DecidePermissionRequest r) {
        return ApiResponse.success(service.decide(appId, versionId, permissionRequestId, r));
    }

    @GetMapping("/api/v1/applications/{appId}/versions/{versionId}/permissions/{permissionRequestId}/history")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Lịch sử permission", description = "Toàn bộ sự kiện request/duyệt của 1 permission, theo thứ tự thời gian (PC-07).")
    public ApiResponse<List<PermissionEventResponse>> history(@PathVariable UUID appId, @PathVariable UUID versionId,
                                                                @PathVariable UUID permissionRequestId) {
        return ApiResponse.success(service.history(appId, versionId, permissionRequestId));
    }
}
