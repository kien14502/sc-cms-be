package com.vnpt.mac.partner.controller;

import com.vnpt.mac.common.response.ApiResponse;
import com.vnpt.mac.partner.dto.UserDtos.*;
import com.vnpt.mac.partner.service.*;
import com.vnpt.mac.security.AuthDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile", description = "Hồ sơ, đổi mật khẩu, 2FA và personal API token của tài khoản đang đăng nhập")
public class ProfileController {
    private final UserService users;
    private final MfaManagementService mfa;
    private final ApiTokenService tokens;

    public ProfileController(UserService users, MfaManagementService mfa, ApiTokenService tokens) {
        this.users = users;
        this.mfa = mfa;
        this.tokens = tokens;
    }

    @GetMapping
    @Operation(summary = "Thông tin tài khoản hiện tại")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(users.me());
    }

    @PatchMapping
    @Operation(summary = "Cập nhật hồ sơ")
    public ApiResponse<UserResponse> profile(@Valid @RequestBody UpdateProfileRequest r) {
        return ApiResponse.success(users.updateProfile(r));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu")
    public ResponseEntity<Void> password(@Valid @RequestBody ChangePasswordRequest r) {
        users.changePassword(r);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Khởi tạo bật 2FA", description = "Sinh secret TOTP mới (chưa kích hoạt cho tới khi gọi /mfa/confirm).")
    public ApiResponse<AuthDtos.MfaSetupResponse> mfaSetup() {
        return ApiResponse.success(mfa.setup());
    }

    @PostMapping("/mfa/confirm")
    @Operation(summary = "Xác nhận và bật 2FA", description = "Xác nhận bằng mã OTP đầu tiên sau khi setup.")
    public ResponseEntity<Void> mfaConfirm(@Valid @RequestBody AuthDtos.MfaCodeRequest r) {
        mfa.confirm(r.code());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mfa")
    @Operation(summary = "Tắt 2FA", description = "Yêu cầu mã OTP hiện tại để xác nhận.")
    public ResponseEntity<Void> mfaDisable(@Valid @RequestBody AuthDtos.MfaCodeRequest r) {
        mfa.disable(r.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api-tokens")
    @PreAuthorize("hasAuthority('token.manage.own')")
    @Operation(summary = "Danh sách personal API token của chính mình")
    public ApiResponse<List<ApiTokenResponse>> apiTokens() {
        return ApiResponse.success(tokens.list());
    }

    @PostMapping("/api-tokens")
    @PreAuthorize("hasAuthority('token.manage.own')")
    @Operation(summary = "Tạo personal API token", description = "Dùng cho CI/CD. Chuỗi token đầy đủ chỉ trả về duy nhất một lần trong response này.")
    public ApiResponse<CreatedApiTokenResponse> createToken(@Valid @RequestBody CreateApiTokenRequest r) {
        return ApiResponse.success(tokens.create(r));
    }

    @PostMapping("/api-tokens/{id}/revoke")
    @PreAuthorize("hasAuthority('token.manage.own')")
    @Operation(summary = "Thu hồi personal API token")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        tokens.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
