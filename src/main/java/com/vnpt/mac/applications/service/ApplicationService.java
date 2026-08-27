package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ApplicationDtos.*;
import com.vnpt.mac.applications.dto.VersionDtos.VersionResponse;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private final ApplicationRepository applications;
    private final AppVersionRepository versions;
    private final VersionService versionService;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ApplicationService(ApplicationRepository applications, AppVersionRepository versions,
                              VersionService versionService, CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versions = versions;
        this.versionService = versionService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        var partnerId = currentUser.partnerId();
        var app = ApplicationEntity.create(partnerId, request.appType());
        applications.save(app);
        var version = versionService.createInitialVersion(app.getId(), partnerId, request.version());
        var response = ApplicationResponse.from(app, 1, VersionResponse.from(version));
        audit.log(partnerId, "APPLICATION_CREATED", "APPLICATION", app.getId(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(ApplicationStatus status, ApplicationType appType, Pageable pageable) {
        var principal = currentUser.require();
        boolean global = principal.authorities().stream().anyMatch(a -> a.getAuthority().equals("app.read.all"))
                || principal.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_REVIEWER"));
        if (!global && principal.partnerId() == null) {
            return Page.empty(pageable);
        }
        UUID scopePartnerId = global ? null : principal.partnerId();
        return applications.search(scopePartnerId, status, appType, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id) {
        return toResponse(require(id));
    }

    public ApplicationEntity require(UUID id) {
        return applications.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private ApplicationResponse toResponse(ApplicationEntity app) {
        long versionCount = versions.countByAppId(app.getId());
        var latest = versions.findTopByAppIdOrderByVersionCodeDesc(app.getId()).map(VersionResponse::from).orElse(null);
        return ApplicationResponse.from(app, versionCount, latest);
    }
}
