package com.vnpt.mac.partner.controller;

import com.vnpt.mac.common.response.*;
import com.vnpt.mac.partner.dto.PartnerDtos.*;
import com.vnpt.mac.partner.entity.PartnerStatus;
import com.vnpt.mac.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partners")
@Tag(name = "Partner", description = "Quản lý partner: CRUD, duyệt/từ chối, tạm ngưng, quota")
public class PartnerController {
    private final PartnerService service;

    public PartnerController(PartnerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partner.read.all')")
    @Operation(summary = "Danh sách partner", description = "Lọc theo status/tên và phân trang. Cần quyền partner.read.all.")
    public ApiResponse<PageResponse<PartnerResponse>> list(@RequestParam(required = false) PartnerStatus status, @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.search(status, q, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partner.create')")
    @Operation(summary = "Tạo partner", description = "Cần quyền partner.create.")
    public ApiResponse<PartnerResponse> create(@Valid @RequestBody CreatePartnerRequest r) {
        return ApiResponse.success(service.create(r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('partner.read') and @resourceAuth.partner(#id)")
    @Operation(summary = "Chi tiết partner", description = "Cần quyền partner.read trên đúng partner sở hữu.")
    public ApiResponse<PartnerResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('partner.read') and @resourceAuth.partner(#id)")
    @Operation(summary = "Lịch sử đổi trạng thái partner")
    public ApiResponse<java.util.List<StatusHistoryResponse>> statusHistory(@PathVariable UUID id) {
        return ApiResponse.success(service.statusHistory(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('partner.update') and @resourceAuth.partner(#id)")
    @Operation(summary = "Cập nhật thông tin partner", description = "Cần quyền partner.update. Có thể trả lỗi CONCURRENT_MODIFICATION nếu revision lệch.")
    public ApiResponse<PartnerResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdatePartnerRequest r) {
        return ApiResponse.success(service.update(id, r));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('partner.approve')")
    @Operation(summary = "Duyệt partner", description = "Chuyển trạng thái PENDING_APPROVAL → ACTIVE. Cần quyền partner.approve.")
    public ApiResponse<PartnerResponse> approve(@PathVariable UUID id) {
        return ApiResponse.success(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('partner.approve')")
    @Operation(summary = "Từ chối partner", description = "Cần quyền partner.approve.")
    public ApiResponse<PartnerResponse> reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return ApiResponse.success(service.reject(id, r.reason()));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('partner.suspend')")
    @Operation(summary = "Tạm ngưng partner", description = "Chuyển trạng thái ACTIVE → SUSPENDED. Cần quyền partner.suspend.")
    public ApiResponse<PartnerResponse> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return ApiResponse.success(service.suspend(id, r.reason()));
    }

    @PostMapping("/{id}/unsuspend")
    @PreAuthorize("hasAuthority('partner.suspend')")
    @Operation(summary = "Bỏ tạm ngưng partner", description = "Cần quyền partner.suspend.")
    public ApiResponse<PartnerResponse> unsuspend(@PathVariable UUID id) {
        return ApiResponse.success(service.unsuspend(id));
    }

    @GetMapping("/{id}/quota")
    @PreAuthorize("hasAuthority('quota.read') and @resourceAuth.partner(#id)")
    @Operation(summary = "Xem quota partner", description = "Cần quyền quota.read trên đúng partner sở hữu.")
    public ApiResponse<QuotaResponse> quota(@PathVariable UUID id) {
        return ApiResponse.success(service.quota(id));
    }

    @PutMapping("/{id}/quota")
    @PreAuthorize("hasAuthority('quota.update')")
    @Operation(summary = "Cập nhật quota partner", description = "Cần quyền quota.update (không giới hạn resource-owner, dành cho platform/admin cấu hình).")
    public ApiResponse<QuotaResponse> quota(@PathVariable UUID id, @Valid @RequestBody UpdateQuotaRequest r) {
        return ApiResponse.success(service.updateQuota(id, r));
    }
}
