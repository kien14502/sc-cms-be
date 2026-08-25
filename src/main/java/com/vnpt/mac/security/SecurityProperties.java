package com.vnpt.mac.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mac.security")
public record SecurityProperties(String jwtSecret, long accessTokenMinutes, long mfaChallengeMinutes, String issuer) {
}
