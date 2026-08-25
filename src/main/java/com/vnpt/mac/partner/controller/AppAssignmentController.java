package com.vnpt.mac.partner.controller;

import com.vnpt.mac.common.response.ApiResponse;
import com.vnpt.mac.partner.dto.UserDtos.AssignDevelopersRequest;
import com.vnpt.mac.partner.service.AppAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/apps/{appId}/developer-assignments")
@Tag(name = "App Assignment", description = "Gán developer cho app (qua M2 ownership port)")
public class AppAssignmentController {
    private final AppAssignmentService service;

    public AppAssignmentController(AppAssignmentService service) {
        this.service = service;
    }

    @PutMapping
    @PreAuthorize("hasAuthority('app.assign') and @resourceAuth.app(#appId)")
    @Operation(summary = "Thay thế danh sách developer của app", description = "Thay thế toàn bộ danh sách. Cần quyền app.assign. M1 fail-closed cho tới khi M2 cung cấp adapter thật.")
    public ApiResponse<List<UUID>> replace(@PathVariable UUID appId, @Valid @RequestBody AssignDevelopersRequest r) {
        return ApiResponse.success(service.replace(appId, r.developerIds()));
    }
}
