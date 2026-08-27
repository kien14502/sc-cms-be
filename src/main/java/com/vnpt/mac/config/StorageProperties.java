package com.vnpt.mac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mac.storage")
public record StorageProperties(String artifactsDir, long maxArtifactBytes) {}
