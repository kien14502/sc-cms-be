package com.vnpt.mac.partner.service;

import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;
import com.vnpt.mac.security.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaManagementService {
    private final UserService userService;
    private final UserMfaMethodRepository methods;
    private final TotpService totp;
    private final SecretCipherService cipher;

    public MfaManagementService(UserService userService, UserMfaMethodRepository methods, TotpService totp, SecretCipherService cipher) {
        this.userService = userService;
        this.methods = methods;
        this.totp = totp;
        this.cipher = cipher;
    }

    @Transactional
    public AuthDtos.MfaSetupResponse setup() {
        var user = userService.requireCurrent();
        String secret = totp.generateSecret();
        methods.save(UserMfaMethodEntity.pending(user.getId(), cipher.encrypt(secret)));
        String uri = "otpauth://totp/MAC:" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8) + "?secret=" + secret + "&issuer=MAC";
        return new AuthDtos.MfaSetupResponse(secret, uri);
    }

    @Transactional
    public void confirm(String code) {
        var user = userService.requireCurrent();
        var m = methods.findFirstByUserIdAndMethodTypeOrderByVerifiedAtDesc(user.getId(), MfaMethodType.TOTP).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_MFA));
        if (!totp.verify(cipher.decrypt(m.getSecretValue()), code))
            throw new BusinessException(ErrorCode.AUTH_INVALID_MFA);
        m.verify();
        user.enableMfa();
    }

    @Transactional
    public void disable(String code) {
        var user = userService.requireCurrent();
        var m = methods.findFirstByUserIdAndMethodTypeOrderByVerifiedAtDesc(user.getId(), MfaMethodType.TOTP).filter(UserMfaMethodEntity::active).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_MFA));
        if (!totp.verify(cipher.decrypt(m.getSecretValue()), code))
            throw new BusinessException(ErrorCode.AUTH_INVALID_MFA);
        m.disable();
        user.disableMfa();
    }
}
