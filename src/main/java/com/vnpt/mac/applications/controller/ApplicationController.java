package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ApplicationDtos.*;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.service.ApplicationService;
import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Application", description = "Quản lý Application: danh sách, tạo (wizard), chi tiết")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('app.read.all') or hasAuthority('app.read')")
    @Operation(summary = "Danh sách application", description = "Lọc theo status/appType/partnerId. Partner Admin/Dev chỉ thấy app của partner mình (partnerId truyền vào bị bỏ qua) trừ khi có app.read.all.")
    public ApiResponse<PageResponse<ApplicationResponse>> list(@RequestParam(required = false) ApplicationStatus status,
                                                               @RequestParam(required = false) ApplicationType appType,
                                                               @RequestParam(required = false) UUID partnerId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.list(status, appType, partnerId, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('app.create')")
    @Operation(summary = "Tạo Application (wizard)", description = "Tạo App shell và version 1 (DRAFT) trong 1 transaction. Cần quyền app.create.")
    public ApiResponse<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest r) {
        return ApiResponse.success(service.create(r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('app.read') and @resourceAuth.app(#id)")
    @Operation(summary = "Chi tiết application", description = "Cần quyền app.read trên app thuộc partner/assignment của mình.")
    public ApiResponse<ApplicationResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }
}
