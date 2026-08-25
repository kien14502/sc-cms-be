package com.vnpt.mac.audit;

import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Tra cứu nhật ký hành động (chỉ đọc)")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Toàn bộ audit log hệ thống", description = "Chỉ role PLATFORM_ADMIN.")
    public ApiResponse<PageResponse<AuditQueryService.AuditResponse>> all(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.all(PageRequest.of(page, Math.min(size, 100)))));
    }

    @GetMapping("/partner/{partnerId}")
    @PreAuthorize("@resourceAuth.partner(#partnerId)")
    @Operation(summary = "Audit log theo partner", description = "Partner admin xem log của chính mình, hoặc platform admin xem log bất kỳ partner nào.")
    public ApiResponse<PageResponse<AuditQueryService.AuditResponse>> partner(@PathVariable UUID partnerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.partner(partnerId, PageRequest.of(page, Math.min(size, 100)))));
    }
}
