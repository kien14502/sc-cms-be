package com.vnpt.mac.security;

import com.vnpt.mac.partner.entity.UserEntity;

import java.util.ArrayList;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class SecurityPrincipalFactory {
    public MacPrincipal from(UserEntity user) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode().name()));
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getCode())));
        });
        return new MacPrincipal(user.getId(), user.getPartnerId(), user.getEmail(), user.getPasswordHash(), authorities);
    }

    public MacPrincipal fromApiToken(UserEntity user, java.util.Set<String> scopes) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode().name())));
        scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority(scope)));
        return new MacPrincipal(user.getId(), user.getPartnerId(), user.getEmail(), null, authorities);
    }
}
