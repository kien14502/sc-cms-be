package com.vnpt.mac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mac.bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String fullName) {}
