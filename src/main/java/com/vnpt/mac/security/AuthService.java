package com.vnpt.mac.security;

import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenService jwt;
    private final UserMfaMethodRepository mfaMethods;
    private final TotpService totp;
    private final SecurityProperties props;
    private final SecretCipherService cipher;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtTokenService jwt, UserMfaMethodRepository mfaMethods, TotpService totp, SecurityProperties props, SecretCipherService cipher) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mfaMethods = mfaMethods;
        this.totp = totp;
        this.props = props;
        this.cipher = cipher;
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email()).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash())).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (user.isMfaEnabled())
            return new AuthDtos.LoginResponse(true, null, jwt.mfaChallenge(user), props.mfaChallengeMinutes() * 60);
        user.recordLogin();
        return new AuthDtos.LoginResponse(false, jwt.accessToken(user), null, props.accessTokenMinutes() * 60);
    }

    @Transactional
    public AuthDtos.LoginResponse verifyMfa(AuthDtos.MfaVerifyRequest request) {
        var claims = jwt.verify(request.challengeToken(), "mfa");
        var id = UUID.fromString(claims.getSubject());
        var user = users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        var method = mfaMethods.findFirstByUserIdAndMethodTypeOrderByVerifiedAtDesc(id, MfaMethodType.TOTP)
                .filter(UserMfaMethodEntity::active).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_MFA));
        if (!totp.verify(cipher.decrypt(method.getSecretValue()), request.code()))
            throw new BusinessException(ErrorCode.AUTH_INVALID_MFA);
        user.recordLogin();
        return new AuthDtos.LoginResponse(false, jwt.accessToken(user), null, props.accessTokenMinutes() * 60);
    }
}
