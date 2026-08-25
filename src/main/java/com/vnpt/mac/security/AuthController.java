package com.vnpt.mac.security;

import com.vnpt.mac.common.response.ApiResponse;
import com.vnpt.mac.partner.dto.UserDtos.AcceptInvitationRequest;
import com.vnpt.mac.partner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng nhập, xác thực 2FA và chấp nhận lời mời tài khoản")
@SecurityRequirements
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Đăng nhập bằng email/password. Nếu tài khoản bật 2FA, trả về challengeToken thay vì accessToken.")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest r) {
        return ApiResponse.success(auth.login(r));
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Xác nhận mã 2FA", description = "Hoàn tất đăng nhập bằng challengeToken + mã OTP 6 số.")
    public ApiResponse<AuthDtos.LoginResponse> verify(@Valid @RequestBody AuthDtos.MfaVerifyRequest r) {
        return ApiResponse.success(auth.verifyMfa(r));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Chấp nhận lời mời", description = "Chấp nhận lời mời tham gia (partner user hoặc admin) và đặt mật khẩu lần đầu.")
    public ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest r) {
        users.accept(r);
        return ResponseEntity.noContent().build();
    }
}
