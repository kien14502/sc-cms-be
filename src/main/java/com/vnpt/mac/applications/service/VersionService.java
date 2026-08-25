package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.VersionDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.AppCategoryRepository;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionService {
    private final AppVersionRepository versions;
    private final ApplicationRepository applications;
    private final AppCategoryRepository categories;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public VersionService(AppVersionRepository versions, ApplicationRepository applications,
                          AppCategoryRepository categories, CurrentUser currentUser, AuditService audit) {
        this.versions = versions;
        this.applications = applications;
        this.categories = categories;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public VersionResponse createVersion(UUID appId, VersionMetadataFields fields) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var entity = createVersionEntity(app.getId(), app.getPartnerId(), nextVersionCode(appId), fields);
        audit.log(app.getPartnerId(), "VERSION_CREATED", "APP_VERSION", entity.getId(), null, VersionResponse.from(entity));
        return VersionResponse.from(entity);
    }

    @Transactional
    AppVersionEntity createInitialVersion(UUID appId, UUID partnerId, VersionMetadataFields fields) {
        return createVersionEntity(appId, partnerId, 1, fields);
    }

    @Transactional(readOnly = true)
    public Page<VersionResponse> listVersions(UUID appId, VersionStatus status, Pageable pageable) {
        requireApp(appId);
        var page = status != null ? versions.findByAppIdAndStatus(appId, status, pageable) : versions.findByAppId(appId, pageable);
        return page.map(VersionResponse::from);
    }

    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID appId, UUID versionId) {
        return VersionResponse.from(requireVersion(appId, versionId));
    }

    @Transactional
    public VersionResponse updateVersion(UUID appId, UUID versionId, UpdateVersionRequest r) {
        var v = requireVersion(appId, versionId);
        var before = VersionResponse.from(v);
        v.assertEditable();
        v.updateMetadata(r.displayName(), r.descriptionShort(), r.descriptionLong(), r.supportedLanguages());
        v.replaceCategories(resolveCategories(r.categoryCodes()));
        audit.log(v.getPartnerId(), "VERSION_UPDATED", "APP_VERSION", v.getId(), before, VersionResponse.from(v));
        return VersionResponse.from(v);
    }

    public AppVersionEntity requireVersion(UUID appId, UUID versionId) {
        var v = versions.findById(versionId).orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND));
        if (!v.getAppId().equals(appId)) throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        return v;
    }

    private void requireApp(UUID appId) {
        if (!applications.existsById(appId)) throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
    }

    private AppVersionEntity createVersionEntity(UUID appId, UUID partnerId, int versionCode, VersionMetadataFields fields) {
        var entity = AppVersionEntity.create(appId, partnerId, versionCode, fields.versionName(), fields.displayName(),
                fields.packageName(), fields.descriptionShort(), fields.descriptionLong(), fields.supportedLanguages());
        entity.replaceCategories(resolveCategories(fields.categoryCodes()));
        return versions.save(entity);
    }

    private int nextVersionCode(UUID appId) {
        return versions.findTopByAppIdOrderByVersionCodeDesc(appId).map(v -> v.getVersionCode() + 1).orElse(1);
    }

    private Set<AppCategoryEntity> resolveCategories(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return Set.of();
        var found = categories.findByCodeIn(codes);
        if (found.size() != codes.size()) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        return new HashSet<>(found);
    }
}
