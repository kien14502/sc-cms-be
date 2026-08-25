package com.vnpt.mac.audit;

import tools.jackson.databind.ObjectMapper;
import com.vnpt.mac.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser,
                        HttpServletRequest request, ObjectMapper objectMapper) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.request = request;
        this.objectMapper = objectMapper;
    }

    public void log(UUID partnerId, String action, String resourceType, UUID resourceId, Object before, Object after) {
        var p = currentUser.require();
        String roles = p.authorities().stream().map(Object::toString).filter(s -> s.startsWith("ROLE_")).sorted().toList().toString();
        repository.save(AuditLogEntity.create(p.userId(), p.email(), roles, partnerId, action, resourceType,
                resourceId, clientIp(), request.getHeader("User-Agent"), json(before), json(after), correlationId()));
    }

    private String correlationId() {
        String value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String clientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"serializationError\":true}";
        }
    }
}
