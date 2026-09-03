package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.VersionDtos.VersionResponse;
import com.vnpt.mac.applications.entity.VersionStatus;
import com.vnpt.mac.applications.service.VersionService;
import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/versions")
@Tag(name = "Review Queue", description = "Danh sách version xuyên suốt mọi application, dùng cho Review Center")
public class VersionQueueController {
    private final VersionService service;

    public VersionQueueController(VersionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('version.review')")
    @Operation(summary = "Hàng đợi review xuyên application", description = "Lọc theo status (vd IN_REVIEW). Cần quyền version.review (không scope theo partner, giống review-decisions).")
    public ApiResponse<PageResponse<VersionResponse>> list(@RequestParam(required = false) VersionStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.listAllVersions(status, PageRequest.of(page, Math.min(size, 100)))));
    }
}
