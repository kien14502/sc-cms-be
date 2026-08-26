package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ArtifactDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.config.StorageProperties;
import com.vnpt.mac.security.CurrentUser;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtifactService {
    private final ApplicationRepository applications;
    private final VersionService versionService;
    private final VersionArtifactRepository artifacts;
    private final VersionWebappConfigRepository webappConfigs;
    private final VersionModuleConfigRepository moduleConfigs;
    private final ValidationRunRepository runs;
    private final ValidationFindingRepository findings;
    private final ArtifactStorageService storage;
    private final ManifestValidationService validation;
    private final StorageProperties storageProperties;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ArtifactService(ApplicationRepository applications, VersionService versionService,
                           VersionArtifactRepository artifacts, VersionWebappConfigRepository webappConfigs,
                           VersionModuleConfigRepository moduleConfigs, ValidationRunRepository runs,
                           ValidationFindingRepository findings, ArtifactStorageService storage,
                           ManifestValidationService validation, StorageProperties storageProperties,
                           CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versionService = versionService;
        this.artifacts = artifacts;
        this.webappConfigs = webappConfigs;
        this.moduleConfigs = moduleConfigs;
        this.runs = runs;
        this.findings = findings;
        this.storage = storage;
        this.validation = validation;
        this.storageProperties = storageProperties;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ArtifactResponse uploadArtifact(UUID appId, UUID versionId, MultipartFile file) {
        var app = requireApp(appId);
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Không đọc được file upload", e);
        }
        ArtifactKind kind;
        ManifestValidationService.ValidationOutcome outcome;
        if (app.getAppType() == ApplicationType.MINIAPP) {
            kind = ArtifactKind.ZIP;
            outcome = validation.validateMiniApp(content, storageProperties.maxArtifactBytes());
        } else if (app.getAppType() == ApplicationType.FEATURE_APP) {
            kind = resolveFeatureAppKind(file.getOriginalFilename());
            outcome = validation.validateFeatureApp(file.getOriginalFilename(), content.length, storageProperties.maxArtifactBytes());
        } else {
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH,
                    "App type " + app.getAppType() + " không nhận artifact qua endpoint này");
        }
        artifacts.findByVersionId(versionId).ifPresent(existing -> {
            storage.delete(existing.getStorageKey());
            artifacts.delete(existing);
        });
        var stored = storage.store(versionId, file.getOriginalFilename(), content);
        var entity = artifacts.save(VersionArtifactEntity.create(versionId, kind, stored.storageKey(),
                file.getOriginalFilename(), stored.sizeBytes(), stored.checksumSha256(), null));
        recordValidationRun(versionId, outcome);
        var response = ArtifactResponse.from(entity);
        audit.log(version.getPartnerId(), "ARTIFACT_UPLOADED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public WebappConfigResponse setWebappConfig(UUID appId, UUID versionId, WebappConfigRequest r) {
        var app = requireApp(appId);
        if (app.getAppType() != ApplicationType.WEBAPP)
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Chỉ WebApp mới cấu hình destination URL");
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var outcome = validation.validateWebapp(r.destinationUrl());
        var existing = webappConfigs.findById(versionId).orElse(null);
        VersionWebappConfigEntity config;
        if (existing != null) {
            existing.update(r.destinationUrl(), outcome.passed());
            config = existing;
        } else {
            config = VersionWebappConfigEntity.create(versionId, r.destinationUrl(), outcome.passed());
        }
        webappConfigs.save(config);
        recordValidationRun(versionId, outcome);
        var response = WebappConfigResponse.from(config);
        audit.log(version.getPartnerId(), "WEBAPP_CONFIG_SET", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public ModuleConfigResponse setModuleConfig(UUID appId, UUID versionId, ModuleConfigRequest r) {
        var app = requireApp(appId);
        if (app.getAppType() != ApplicationType.APP_MODULE)
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Chỉ App Module mới cấu hình module metadata");
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var outcome = validation.validateModule(r.moduleNamespace());
        var existing = moduleConfigs.findById(versionId).orElse(null);
        VersionModuleConfigEntity config;
        if (existing != null) {
            existing.update(r.moduleNamespace(), r.description());
            config = existing;
        } else {
            config = VersionModuleConfigEntity.create(versionId, r.moduleNamespace(), r.description());
        }
        moduleConfigs.save(config);
        recordValidationRun(versionId, outcome);
        var response = ModuleConfigResponse.from(config);
        audit.log(version.getPartnerId(), "MODULE_CONFIG_SET", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ValidationRunResponse latestValidation(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        var run = runs.findTopByVersionIdOrderByStartedAtDesc(versionId).orElse(null);
        if (run == null) return ValidationRunResponse.empty(versionId);
        return ValidationRunResponse.from(run, findings.findByRunId(run.getId()));
    }

    private void recordValidationRun(UUID versionId, ManifestValidationService.ValidationOutcome outcome) {
        var run = ValidationRunEntity.start(versionId, currentUser.id());
        run.complete(outcome.passed());
        runs.save(run);
        outcome.findings().forEach(f -> findings.save(
                ValidationFindingEntity.create(run.getId(), f.ruleCode(), f.severity(), f.message(), Map.of())));
    }

    private ArtifactKind resolveFeatureAppKind(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".apk")) return ArtifactKind.APK;
        if (lower.endsWith(".aab")) return ArtifactKind.AAB;
        throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Feature App yêu cầu file .apk hoặc .aab");
    }

    private ApplicationEntity requireApp(UUID appId) {
        return applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }
}
