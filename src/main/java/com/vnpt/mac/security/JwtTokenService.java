package com.vnpt.mac.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.partner.entity.UserEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final SecurityProperties properties;
    private final byte[] secret;

    public JwtTokenService(SecurityProperties properties) {
        this.properties = properties;
        this.secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("JWT secret must be at least 32 bytes");
    }

    public String accessToken(UserEntity user) {
        return sign(user, "access", properties.accessTokenMinutes());
    }

    public String mfaChallenge(UserEntity user) {
        return sign(user, "mfa", properties.mfaChallengeMinutes());
    }

    private String sign(UserEntity user, String type, long minutes) {
        try {
            var now = Instant.now();
            var roles = user.getRoles().stream().map(r -> r.getCode().name()).toList();
            var claims = new JWTClaimsSet.Builder()
                    .issuer(properties.issuer()).subject(user.getId().toString())
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(minutes * 60)))
                    .jwtID(UUID.randomUUID().toString()).claim("type", type)
                    .claim("email", user.getEmail()).claim("partnerId", user.getPartnerId() == null ? null : user.getPartnerId().toString())
                    .claim("roles", roles).build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign token", e);
        }
    }

    public JWTClaimsSet verify(String token, String expectedType) {
        try {
            var jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            var claims = jwt.getJWTClaimsSet();
            if (!properties.issuer().equals(claims.getIssuer()) || claims.getExpirationTime().before(new Date())
                    || !expectedType.equals(claims.getStringClaim("type"))) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }
            return claims;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }
}
