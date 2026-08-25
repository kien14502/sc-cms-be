package com.vnpt.mac.partner.controller;

import com.vnpt.mac.common.response.ApiResponse;
import com.vnpt.mac.partner.dto.UserDtos.*;
import com.vnpt.mac.partner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import com.vnpt.mac.common.response.PageResponse;

@RestController
@RequestMapping("/api/v1/admin-users")
@Tag(name = "Admin User", description = "Quản lý tài khoản nội bộ (platform admin/reviewer...), không gắn với partner")
public class AdminUserController {
    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('admin.manage')")
    @Operation(summary = "Danh sách admin", description = "Cần quyền admin.manage.")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(users.listAdmins(PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin.manage')")
    @Operation(summary = "Tạo tài khoản admin mới", description = "Cần quyền admin.manage. Trả về invitationToken.")
    public ApiResponse<InvitationResponse> create(@Valid @RequestBody CreateAdminRequest r) {
        return ApiResponse.success(users.createAdmin(r));
    }
}
