package com.vnpt.mac.security;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public MacPrincipal require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MacPrincipal principal)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        return principal;
    }

    public UUID id() {
        return require().userId();
    }

    public UUID partnerId() {
        return require().partnerId();
    }
}
