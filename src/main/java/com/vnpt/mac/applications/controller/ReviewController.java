package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ReviewDtos.*;
import com.vnpt.mac.applications.service.ReviewService;
import com.vnpt.mac.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions/{versionId}")
@Tag(name = "Review", description = "Submit, quyết định review, lịch sử review")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('version.submit') and @resourceAuth.app(#appId)")
    @Operation(summary = "Submit version để review", description = "Yêu cầu validation gần nhất PASSED (trừ App2App). Cần quyền version.submit.")
    public ApiResponse<ReviewSubmissionResponse> submit(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.submit(appId, versionId));
    }

    @PostMapping("/review-decisions")
    @PreAuthorize("hasAuthority('version.review')")
    @Operation(summary = "Approve/Reject/Request changes", description = "feedback bắt buộc khi REJECT hoặc REQUEST_CHANGES. Cần quyền version.review (không scope theo partner vì Reviewer làm việc xuyên partner).")
    public ApiResponse<ReviewSubmissionResponse> decide(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody ReviewDecisionRequest r) {
        return ApiResponse.success(service.decide(appId, versionId, r));
    }

    @GetMapping("/review-history")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Lịch sử review", description = "Toàn bộ các lượt submit và quyết định, theo thứ tự thời gian.")
    public ApiResponse<List<ReviewSubmissionResponse>> history(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.history(appId, versionId));
    }
}
