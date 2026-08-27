package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.CapabilityDtos.*;
import com.vnpt.mac.applications.service.CapabilityService;
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
@Tag(name = "Capability", description = "Capability catalog và khai báo/duyệt capability theo version (M4)")
public class CapabilityController {
    private final CapabilityService service;

    public CapabilityController(CapabilityService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/capabilities/catalog")
    @PreAuthorize("hasAuthority('capability.catalog.read')")
    @Operation(summary = "Danh mục capability", description = "Danh sách capability đang active. Cần quyền capability.catalog.read.")
    public ApiResponse<List<CapabilityCatalogResponse>> catalog() {
        return ApiResponse.success(service.listCatalog());
    }

    @GetMapping("/api/v1/capabilities/catalog/all")
    @PreAuthorize("hasAuthority('capability.catalog.manage')")
    @Operation(summary = "Danh mục capability (kể cả inactive)", description = "Dùng cho màn quản trị catalog. Cần quyền capability.catalog.manage.")
    public ApiResponse<List<CapabilityCatalogResponse>> allCatalog() {
        return ApiResponse.success(service.listAllCatalog());
    }

    @PostMapping("/api/v1/capabilities/catalog")
    @PreAuthorize("hasAuthority('capability.catalog.manage')")
    @Operation(summary = "Tạo capability catalog entry", description = "Cần quyền capability.catalog.manage (Platform Admin).")
    public ApiResponse<CapabilityCatalogResponse> createCatalogEntry(@Valid @RequestBody CreateCapabilityCatalogRequest r) {
        return ApiResponse.success(service.createCatalogEntry(r));
    }

    @PatchMapping("/api/v1/capabilities/catalog/{capabilityId}")
    @PreAuthorize("hasAuthority('capability.catalog.manage')")
    @Operation(summary = "Cập nhật capability catalog entry", description = "Cập nhật displayName/allowedAppTypes/isActive. Cần quyền capability.catalog.manage.")
    public ApiResponse<CapabilityCatalogResponse> updateCatalogEntry(@PathVariable UUID capabilityId,
                                                                      @Valid @RequestBody UpdateCapabilityCatalogRequest r) {
        return ApiResponse.success(service.updateCatalogEntry(capabilityId, r));
    }

    @GetMapping("/api/v1/applications/{appId}/versions/{versionId}/capabilities")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Danh sách capability đã khai báo trên version")
    public ApiResponse<List<AppVersionCapabilityResponse>> list(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.listForVersion(appId, versionId));
    }

    @PostMapping("/api/v1/applications/{appId}/versions/{versionId}/capabilities")
    @PreAuthorize("hasAuthority('capability.request') and @resourceAuth.app(#appId)")
    @Operation(summary = "Khai báo capability", description = "Chọn capability từ catalog. Cần quyền capability.request.")
    public ApiResponse<AppVersionCapabilityResponse> request(@PathVariable UUID appId, @PathVariable UUID versionId,
                                                              @Valid @RequestBody RequestCapabilityRequest r) {
        return ApiResponse.success(service.requestCapability(appId, versionId, r));
    }

    @DeleteMapping("/api/v1/applications/{appId}/versions/{versionId}/capabilities/{capabilityRequestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('capability.request') and @resourceAuth.app(#appId)")
    @Operation(summary = "Gỡ capability đã khai báo", description = "Chỉ khi version ở DRAFT/CHANGES_REQUESTED.")
    public void remove(@PathVariable UUID appId, @PathVariable UUID versionId, @PathVariable UUID capabilityRequestId) {
        service.remove(appId, versionId, capabilityRequestId);
    }

    @PostMapping("/api/v1/applications/{appId}/versions/{versionId}/capabilities/{capabilityRequestId}/decide")
    @PreAuthorize("hasAuthority('capability.decide')")
    @Operation(summary = "Duyệt/Từ chối capability", description = "reason bắt buộc khi REJECT. Cần quyền capability.decide (Reviewer/Platform Admin, không scope theo partner).")
    public ApiResponse<AppVersionCapabilityResponse> decide(@PathVariable UUID appId, @PathVariable UUID versionId,
                                                             @PathVariable UUID capabilityRequestId,
                                                             @Valid @RequestBody DecideCapabilityRequest r) {
        return ApiResponse.success(service.decide(appId, versionId, capabilityRequestId, r));
    }
}
