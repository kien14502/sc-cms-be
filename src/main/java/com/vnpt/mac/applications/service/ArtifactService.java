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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
            String oldStorageKey = existing.getStorageKey();
            artifacts.delete(existing);
            // Physically delete the old file only once the DB row deletion is durable, so a
            // later rollback in this same transaction (e.g. recordValidationRun or audit.log
            // throwing) can't leave the DB pointing at a file we already removed from disk.
            runAfterCommit(() -> storage.delete(oldStorageKey));
        });
        artifacts.flush();
        var stored = storage.store(versionId, file.getOriginalFilename(), content);
        // If this transaction rolls back after the file is physically written (but before the
        // referencing row commits), clean up the orphaned file instead of leaking it on disk.
        runOnRollback(() -> storage.delete(stored.storageKey()));
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

    /** Runs {@code action} once the current transaction commits, deferring an irreversible
     * filesystem side effect until the DB state it depends on is durable. Falls back to running
     * immediately when no Spring transaction is active (e.g. plain-Mockito unit tests that call
     * this service outside any transactional proxy) — in that case there is no commit to wait
     * for, so running now preserves the pre-fix behavior. */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Runs {@code action} only if the current transaction rolls back, to compensate for a
     * filesystem write that already happened but whose referencing DB row never committed.
     * When no Spring transaction is active there is nothing to roll back, so this is a no-op. */
    private void runOnRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        }
    }
}
