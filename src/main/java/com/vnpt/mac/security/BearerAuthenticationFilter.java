package com.vnpt.mac.security;

import com.vnpt.mac.partner.entity.UserStatus;
import com.vnpt.mac.partner.repository.UserApiTokenRepository;
import com.vnpt.mac.partner.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwt;
    private final UserRepository users;
    private final UserApiTokenRepository apiTokens;
    private final PasswordEncoder encoder;
    private final SecurityPrincipalFactory principals;

    public BearerAuthenticationFilter(JwtTokenService jwt, UserRepository users, UserApiTokenRepository apiTokens,
                                      PasswordEncoder encoder, SecurityPrincipalFactory principals) {
        this.jwt = jwt;
        this.users = users;
        this.apiTokens = apiTokens;
        this.encoder = encoder;
        this.principals = principals;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                var result = token.startsWith("mac_pat_") ? authenticatePat(token) : new Authenticated(authenticateJwt(token), null);
                if (result != null && result.user() != null && result.user().getStatus() == UserStatus.ACTIVE) {
                    var principal = result.scopes() == null ? principals.from(result.user()) : principals.fromApiToken(result.user(), result.scopes());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, token, principal.authorities()));
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private com.vnpt.mac.partner.entity.UserEntity authenticateJwt(String token) throws Exception {
        var claims = jwt.verify(token, "access");
        return users.findById(UUID.fromString(claims.getSubject())).orElse(null);
    }

    private Authenticated authenticatePat(String raw) {
        String[] parts = raw.split("_", 4);
        if (parts.length != 4) return null;
        String prefix = parts[2];
        return apiTokens.findByTokenPrefixAndRevokedAtIsNull(prefix).stream()
                .filter(t -> t.usable() && encoder.matches(raw, t.getTokenHash()))
                .findFirst().map(t -> {
                    t.used();
                    apiTokens.save(t);
                    return new Authenticated(users.findById(t.getUserId()).orElse(null), java.util.Set.of(t.getScopes().split(",")));
                }).orElse(null);
    }

    private record Authenticated(com.vnpt.mac.partner.entity.UserEntity user, java.util.Set<String> scopes) {
    }
}
