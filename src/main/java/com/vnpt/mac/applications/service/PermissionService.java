package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.PermissionDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {
    private final ApplicationRepository applications;
    private final AppVersionRepository versions;
    private final VersionService versionService;
    private final PermissionCatalogRepository catalog;
    private final PermissionAppTypeRuleRepository rules;
    private final AppVersionPermissionRepository versionPermissions;
    private final PermissionEventRepository events;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public PermissionService(ApplicationRepository applications, AppVersionRepository versions, VersionService versionService,
                             PermissionCatalogRepository catalog, PermissionAppTypeRuleRepository rules,
                             AppVersionPermissionRepository versionPermissions, PermissionEventRepository events,
                             CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versions = versions;
        this.versionService = versionService;
        this.catalog = catalog;
        this.rules = rules;
        this.versionPermissions = versionPermissions;
        this.events = events;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PermissionCatalogResponse> listCatalog() {
        return catalog.findByIsActiveTrue().stream().map(PermissionCatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionCatalogResponse> listAllCatalog() {
        return catalog.findAllByOrderByCodeAsc().stream().map(PermissionCatalogResponse::from).toList();
    }

    @Transactional
    public PermissionCatalogResponse createCatalogEntry(CreatePermissionCatalogRequest r) {
        if (catalog.existsByCode(r.code()))
            throw new BusinessException(ErrorCode.PERMISSION_CATALOG_CODE_DUPLICATE, "Permission code " + r.code() + " đã tồn tại");
        var entity = catalog.save(PermissionCatalogEntity.create(r.code(), r.displayName(), r.sensitivity(), r.requiresManualReview()));
        var response = PermissionCatalogResponse.from(entity);
        audit.log(null, "PERMISSION_CATALOG_CREATED", "PERMISSION_CATALOG", entity.getId(), null, response);
        return response;
    }

    @Transactional
    public PermissionCatalogResponse updateCatalogEntry(UUID permissionId, UpdatePermissionCatalogRequest r) {
        var entity = requireCatalogEntry(permissionId);
        entity.update(r.displayName(), r.sensitivity(), r.requiresManualReview(), r.isActive());
        var response = PermissionCatalogResponse.from(entity);
        audit.log(null, "PERMISSION_CATALOG_UPDATED", "PERMISSION_CATALOG", entity.getId(), null, response);
        return response;
    }

    @Transactional
    public AppTypeRuleResponse upsertAppTypeRule(UUID permissionId, ApplicationType appType, UpsertAppTypeRuleRequest r) {
        requireCatalogEntry(permissionId);
        var rule = rules.findByPermissionIdAndAppType(permissionId, appType)
                .map(existing -> { existing.update(r.effect(), r.reason()); return existing; })
                .orElseGet(() -> rules.save(PermissionAppTypeRuleEntity.create(permissionId, appType, r.effect(), r.reason())));
        var response = AppTypeRuleResponse.from(rule);
        audit.log(null, "PERMISSION_APP_TYPE_RULE_UPSERTED", "PERMISSION_APP_TYPE_RULE", rule.getId(), null, response);
        return response;
    }

    @Transactional
    public void deleteAppTypeRule(UUID permissionId, ApplicationType appType) {
        var rule = rules.findByPermissionIdAndAppType(permissionId, appType)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_APP_TYPE_RULE_NOT_FOUND));
        rules.delete(rule);
        audit.log(null, "PERMISSION_APP_TYPE_RULE_DELETED", "PERMISSION_APP_TYPE_RULE", rule.getId(), rule, null);
    }

    @Transactional(readOnly = true)
    public List<AppVersionPermissionResponse> listForVersion(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        return versionPermissions.findByVersionIdOrderByCreatedAtAsc(versionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppVersionPermissionResponse requestPermission(UUID appId, UUID versionId, RequestPermissionRequest r) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();

        var permission = catalog.findByCodeAndIsActiveTrue(r.permissionCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_CATALOG_NOT_FOUND));

        if (versionPermissions.findByVersionIdAndPermissionId(versionId, permission.getId()).isPresent())
            throw new BusinessException(ErrorCode.PERMISSION_ALREADY_REQUESTED,
                    "Permission " + permission.getCode() + " đã được khai báo cho version này");

        var ruleEffect = rules.findByPermissionIdAndAppType(permission.getId(), app.getAppType())
                .map(PermissionAppTypeRuleEntity::getEffect)
                .orElse(RuleEffect.ALLOW);

        boolean isEscalation = detectEscalation(app.getId(), versionId, permission);

        PermissionRequestStatus initialStatus;
        if (ruleEffect == RuleEffect.DENY) {
            initialStatus = PermissionRequestStatus.BLOCKED;
        } else if (permission.getSensitivity() == PermissionSensitivity.NORMAL && !isEscalation) {
            initialStatus = PermissionRequestStatus.AUTO_APPROVED;
        } else {
            initialStatus = PermissionRequestStatus.PENDING_REVIEW;
        }

        var entity = versionPermissions.save(AppVersionPermissionEntity.request(versionId, permission.getId(),
                r.justification(), permission.getSensitivity(), initialStatus, isEscalation));
        events.save(PermissionEventEntity.create(entity.getId(), "REQUESTED", currentUser.id(),
                "Yêu cầu quyền " + permission.getCode() + " -> " + initialStatus));

        var response = toResponse(entity, permission);
        audit.log(version.getPartnerId(), "PERMISSION_REQUESTED", "APP_VERSION_PERMISSION", entity.getId(), null, response);
        return response;
    }

    @Transactional
    public void remove(UUID appId, UUID versionId, UUID permissionRequestId) {
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var entity = requirePermissionRequest(versionId, permissionRequestId);
        // permission_events.app_version_permission_id is a NOT NULL FK with no ON DELETE CASCADE
        // (V010), so the child events must be deleted and flushed before the parent request row
        // is deleted, otherwise the delete violates that FK at flush time.
        events.deleteByAppVersionPermissionId(entity.getId());
        events.flush();
        versionPermissions.delete(entity);
        audit.log(version.getPartnerId(), "PERMISSION_REMOVED", "APP_VERSION_PERMISSION", permissionRequestId, entity, null);
    }

    @Transactional
    public AppVersionPermissionResponse decide(UUID appId, UUID versionId, UUID permissionRequestId, DecidePermissionRequest r) {
        var version = versionService.requireVersion(appId, versionId);
        var entity = requirePermissionRequest(versionId, permissionRequestId);

        if (r.decision() == PermissionDecisionType.REJECT && (r.reason() == null || r.reason().isBlank()))
            throw new BusinessException(ErrorCode.PERMISSION_DECISION_REASON_REQUIRED);

        var decidedBy = currentUser.id();
        if (r.decision() == PermissionDecisionType.APPROVE) {
            entity.approve(decidedBy);
        } else {
            entity.reject(decidedBy, r.reason());
        }
        events.save(PermissionEventEntity.create(entity.getId(), "DECIDED", decidedBy,
                r.decision() + (r.reason() != null ? ": " + r.reason() : "")));

        var permission = requireCatalogEntry(entity.getPermissionId());
        var response = toResponse(entity, permission);
        audit.log(version.getPartnerId(), "PERMISSION_DECIDED", "APP_VERSION_PERMISSION", entity.getId(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<PermissionEventResponse> history(UUID appId, UUID versionId, UUID permissionRequestId) {
        versionService.requireVersion(appId, versionId);
        var entity = requirePermissionRequest(versionId, permissionRequestId);
        return events.findByAppVersionPermissionIdOrderByCreatedAtAsc(entity.getId()).stream()
                .map(PermissionEventResponse::from)
                .toList();
    }

    /**
     * PC-06: compare against the app's most recently APPROVED version. No prior approved
     * version means every permission on this version is new -> escalation.
     */
    private boolean detectEscalation(UUID appId, UUID currentVersionId, PermissionCatalogEntity permission) {
        var base = versions.findTopByAppIdAndStatusOrderByVersionCodeDesc(appId, VersionStatus.APPROVED);
        if (base.isEmpty() || base.get().getId().equals(currentVersionId)) return true;

        var baseApproved = versionPermissions.findByVersionIdAndStatus(base.get().getId(), PermissionRequestStatus.APPROVED);
        var basePermission = baseApproved.stream()
                .filter(p -> p.getPermissionId().equals(permission.getId()))
                .findFirst();
        if (basePermission.isEmpty()) return true;
        return permission.getSensitivity().ordinal() > basePermission.get().getResolvedSensitivity().ordinal();
    }

    private AppVersionPermissionEntity requirePermissionRequest(UUID versionId, UUID permissionRequestId) {
        var entity = versionPermissions.findById(permissionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_REQUEST_NOT_FOUND));
        if (!entity.getVersionId().equals(versionId)) throw new BusinessException(ErrorCode.PERMISSION_REQUEST_NOT_FOUND);
        return entity;
    }

    private PermissionCatalogEntity requireCatalogEntry(UUID permissionId) {
        return catalog.findById(permissionId).orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_CATALOG_NOT_FOUND));
    }

    private AppVersionPermissionResponse toResponse(AppVersionPermissionEntity e) {
        return toResponse(e, requireCatalogEntry(e.getPermissionId()));
    }

    private AppVersionPermissionResponse toResponse(AppVersionPermissionEntity e, PermissionCatalogEntity permission) {
        return AppVersionPermissionResponse.from(e, permission);
    }
}
