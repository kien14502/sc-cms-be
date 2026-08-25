package com.vnpt.mac.partner.service;

import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.dto.UserDtos.*;
import com.vnpt.mac.partner.entity.UserApiTokenEntity;
import com.vnpt.mac.partner.repository.UserApiTokenRepository;
import com.vnpt.mac.security.CurrentUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiTokenService {
    private static final Set<String> ALLOWED = Set.of("artifact.upload", "version.create", "version.submit", "app.read");
    private final UserApiTokenRepository repository;
    private final TokenValueService values;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final int defaultDays;

    public ApiTokenService(UserApiTokenRepository repository, TokenValueService values, PasswordEncoder encoder, CurrentUser current, @Value("${mac.api-token.default-expiration-days:90}") int defaultDays) {
        this.repository = repository;
        this.values = values;
        this.encoder = encoder;
        this.current = current;
        this.defaultDays = defaultDays;
    }

    @Transactional
    public CreatedApiTokenResponse create(CreateApiTokenRequest r) {
        if (!ALLOWED.containsAll(r.scopes()))
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Scope không hợp lệ");
        String prefix = values.random(6).substring(0, 8);
        String raw = "mac_pat_" + prefix + "_" + values.random(32);
        int days = r.expiresInDays() == null ? defaultDays : r.expiresInDays();
        Instant expires = Instant.now().plus(days, ChronoUnit.DAYS);
        var e = repository.save(UserApiTokenEntity.create(current.id(), r.name(), prefix, encoder.encode(raw), String.join(",", new TreeSet<>(r.scopes())), expires));
        return new CreatedApiTokenResponse(e.getId(), raw, prefix, r.scopes(), expires);
    }

    @Transactional(readOnly = true)
    public List<ApiTokenResponse> list() {
        return repository.findByUserIdOrderByCreatedAtDesc(current.id()).stream().map(ApiTokenResponse::from).toList();
    }

    @Transactional
    public void revoke(UUID id) {
        var t = repository.findByIdAndUserId(id, current.id()).orElseThrow(() -> new BusinessException(ErrorCode.API_TOKEN_NOT_FOUND));
        t.revoke();
    }
}
