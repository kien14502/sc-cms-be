package com.vnpt.mac.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> all(Pageable p) {
        return repository.findAll(p).map(AuditResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> partner(UUID id, Pageable p) {
        return repository.findByPartnerId(id, p).map(AuditResponse::from);
    }

    public record AuditResponse(UUID id, UUID actorUserId, String actorEmail, String actorRoles, UUID partnerId,
                                String action, String resourceType, UUID resourceId, String ipAddress, String userAgent,
                                String beforeState, String afterState, String correlationId, Instant createdAt) {
        static AuditResponse from(AuditLogEntity e) {
            return new AuditResponse(e.getId(), e.getActorUserId(), e.getActorEmail(), e.getActorRoles(), e.getPartnerId(), e.getAction(), e.getResourceType(), e.getResourceId(), e.getIpAddress(), e.getUserAgent(), e.getBeforeState(), e.getAfterState(), e.getCorrelationId(), e.getCreatedAt());
        }
    }
}
