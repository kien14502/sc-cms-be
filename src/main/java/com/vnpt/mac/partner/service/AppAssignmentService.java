package com.vnpt.mac.partner.service;

import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;
import com.vnpt.mac.security.CurrentUser;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppAssignmentService {
    private final AppDeveloperAssignmentRepository assignments;
    private final UserRepository users;
    private final CurrentUser current;
    private final AuditService audit;
    private final ApplicationOwnershipPort ownership;

    public AppAssignmentService(AppDeveloperAssignmentRepository assignments, UserRepository users, CurrentUser current, AuditService audit, ApplicationOwnershipPort ownership) {
        this.assignments = assignments;
        this.users = users;
        this.current = current;
        this.audit = audit;
        this.ownership = ownership;
    }

    @Transactional
    public List<UUID> replace(UUID appId, List<UUID> developerIds) {
        var actor = current.require();
        if (actor.partnerId() == null || !ownership.belongsToPartner(appId, actor.partnerId()))
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "M2 chưa xác nhận App thuộc Partner");
        var desired = new HashSet<>(developerIds);
        var entities = users.findAllById(desired);
        if (entities.size() != desired.size() || entities.stream().anyMatch(u -> u.getStatus() != UserStatus.ACTIVE || u.getPartnerId() == null || !u.getPartnerId().equals(actor.partnerId())))
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID);
        var active = assignments.findByAppIdAndRevokedAtIsNull(appId);
        active.stream().filter(a -> !desired.contains(a.getUserId())).forEach(AppDeveloperAssignmentEntity::revoke);
        var existing = active.stream().map(AppDeveloperAssignmentEntity::getUserId).collect(java.util.stream.Collectors.toSet());
        desired.stream().filter(id -> !existing.contains(id)).forEach(id -> assignments.save(AppDeveloperAssignmentEntity.grant(appId, id, actor.userId())));
        audit.log(actor.partnerId(), "APP_DEVELOPERS_ASSIGNED", "APP", appId, null, desired);
        return new ArrayList<>(desired);
    }
}
