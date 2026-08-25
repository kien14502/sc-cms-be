package com.vnpt.mac.security;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginResponse(boolean mfaRequired, String accessToken, String challengeToken, long expiresInSeconds) {
    }

    public record MfaVerifyRequest(@NotBlank String challengeToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {
    }

    public record MfaCodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }
}
