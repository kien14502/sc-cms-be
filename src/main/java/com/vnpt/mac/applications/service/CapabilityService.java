package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.CapabilityDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.applications.repository.AppVersionCapabilityRepository;
import com.vnpt.mac.applications.repository.CapabilityCatalogRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilityService {
    private final ApplicationRepository applications;
    private final VersionService versionService;
    private final CapabilityCatalogRepository catalog;
    private final AppVersionCapabilityRepository versionCapabilities;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public CapabilityService(ApplicationRepository applications, VersionService versionService,
                             CapabilityCatalogRepository catalog, AppVersionCapabilityRepository versionCapabilities,
                             CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versionService = versionService;
        this.catalog = catalog;
        this.versionCapabilities = versionCapabilities;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CapabilityCatalogResponse> listCatalog() {
        return catalog.findByIsActiveTrue().stream().map(CapabilityCatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CapabilityCatalogResponse> listAllCatalog() {
        return catalog.findAllByOrderByCodeAsc().stream().map(CapabilityCatalogResponse::from).toList();
    }

    @Transactional
    public CapabilityCatalogResponse createCatalogEntry(CreateCapabilityCatalogRequest r) {
        if (catalog.existsByCode(r.code()))
            throw new BusinessException(ErrorCode.CAPABILITY_CATALOG_CODE_DUPLICATE, "Capability code " + r.code() + " đã tồn tại");
        var entity = catalog.save(CapabilityCatalogEntity.create(r.code(), r.displayName(), r.allowedAppTypes()));
        var response = CapabilityCatalogResponse.from(entity);
        audit.log(null, "CAPABILITY_CATALOG_CREATED", "CAPABILITY_CATALOG", entity.getId(), null, response);
        return response;
    }

    @Transactional
    public CapabilityCatalogResponse updateCatalogEntry(UUID capabilityId, UpdateCapabilityCatalogRequest r) {
        var entity = requireCatalogEntry(capabilityId);
        entity.update(r.displayName(), r.allowedAppTypes(), r.isActive());
        var response = CapabilityCatalogResponse.from(entity);
        audit.log(null, "CAPABILITY_CATALOG_UPDATED", "CAPABILITY_CATALOG", entity.getId(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<AppVersionCapabilityResponse> listForVersion(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        return versionCapabilities.findByVersionIdOrderByCreatedAtAsc(versionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppVersionCapabilityResponse requestCapability(UUID appId, UUID versionId, RequestCapabilityRequest r) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();

        var capability = catalog.findByCodeAndIsActiveTrue(r.capabilityCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAPABILITY_CATALOG_NOT_FOUND));

        if (versionCapabilities.findByVersionIdAndCapabilityId(versionId, capability.getId()).isPresent())
            throw new BusinessException(ErrorCode.CAPABILITY_ALREADY_REQUESTED,
                    "Capability " + capability.getCode() + " đã được khai báo cho version này");

        var initialStatus = capability.allowsAppType(app.getAppType())
                ? CapabilityRequestStatus.PENDING_REVIEW
                : CapabilityRequestStatus.BLOCKED;

        var entity = versionCapabilities.save(AppVersionCapabilityEntity.request(versionId, capability.getId(), initialStatus));
        var response = toResponse(entity, capability);
        audit.log(version.getPartnerId(), "CAPABILITY_REQUESTED", "APP_VERSION_CAPABILITY", entity.getId(), null, response);
        return response;
    }

    @Transactional
    public void remove(UUID appId, UUID versionId, UUID capabilityRequestId) {
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var entity = requireCapabilityRequest(versionId, capabilityRequestId);
        versionCapabilities.delete(entity);
        audit.log(version.getPartnerId(), "CAPABILITY_REMOVED", "APP_VERSION_CAPABILITY", capabilityRequestId, entity, null);
    }

    @Transactional
    public AppVersionCapabilityResponse decide(UUID appId, UUID versionId, UUID capabilityRequestId, DecideCapabilityRequest r) {
        var version = versionService.requireVersion(appId, versionId);
        var entity = requireCapabilityRequest(versionId, capabilityRequestId);

        if (r.decision() == CapabilityDecisionType.REJECT && (r.reason() == null || r.reason().isBlank()))
            throw new BusinessException(ErrorCode.CAPABILITY_DECISION_REASON_REQUIRED);

        var decidedBy = currentUser.id();
        if (r.decision() == CapabilityDecisionType.APPROVE) {
            entity.approve(decidedBy);
        } else {
            entity.reject(decidedBy, r.reason());
        }

        var capability = requireCatalogEntry(entity.getCapabilityId());
        var response = toResponse(entity, capability);
        audit.log(version.getPartnerId(), "CAPABILITY_DECIDED", "APP_VERSION_CAPABILITY", entity.getId(), null, response);
        return response;
    }

    private AppVersionCapabilityEntity requireCapabilityRequest(UUID versionId, UUID capabilityRequestId) {
        var entity = versionCapabilities.findById(capabilityRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAPABILITY_REQUEST_NOT_FOUND));
        if (!entity.getVersionId().equals(versionId)) throw new BusinessException(ErrorCode.CAPABILITY_REQUEST_NOT_FOUND);
        return entity;
    }

    private CapabilityCatalogEntity requireCatalogEntry(UUID capabilityId) {
        return catalog.findById(capabilityId).orElseThrow(() -> new BusinessException(ErrorCode.CAPABILITY_CATALOG_NOT_FOUND));
    }

    private AppVersionCapabilityResponse toResponse(AppVersionCapabilityEntity e) {
        return toResponse(e, requireCatalogEntry(e.getCapabilityId()));
    }

    private AppVersionCapabilityResponse toResponse(AppVersionCapabilityEntity e, CapabilityCatalogEntity capability) {
        return AppVersionCapabilityResponse.from(e, capability);
    }
}
