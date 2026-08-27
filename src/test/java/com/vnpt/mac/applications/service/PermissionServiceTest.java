package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.PermissionDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PermissionService (M3 permission-catalog domain, wired into the M2
 * submit/approve gates via ReviewService). Mirrors ReviewServiceTest/ArtifactServiceTest's
 * mocked-repository pattern: Mockito-only, no Spring context, real domain entities exercised
 * through their own create()/state-machine methods.
 *
 * The remove()-ordering test is a genuine regression test (written before the fix, per TDD):
 * app_version_permissions is deleted while permission_events still holds a NOT NULL FK to it
 * with no ON DELETE CASCADE (see V010__create_permission_catalog_domain.sql), so removing a
 * permission request without first clearing its events would violate that FK at flush time —
 * the same class of Hibernate flush-ordering bug ArtifactServiceTest already regression-tests
 * for artifact re-upload.
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock ApplicationRepository applications;
    @Mock AppVersionRepository versions;
    @Mock VersionService versionService;
    @Mock PermissionCatalogRepository catalog;
    @Mock PermissionAppTypeRuleRepository rules;
    @Mock AppVersionPermissionRepository versionPermissions;
    @Mock PermissionEventRepository events;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private PermissionService newService() {
        return new PermissionService(applications, versions, versionService, catalog, rules,
                versionPermissions, events, currentUser, audit);
    }

    private AppVersionEntity draftVersion(UUID appId, UUID partnerId) {
        return AppVersionEntity.create(appId, partnerId, 1, "1.0.0", "Display", "com.example.pkg", null, null, List.of());
    }

    private PermissionCatalogEntity catalogEntry(UUID id, String code, PermissionSensitivity sensitivity) {
        var entity = newInstance(PermissionCatalogEntity.class);
        setField(entity, "id", id);
        setField(entity, "code", code);
        setField(entity, "displayName", code);
        setField(entity, "sensitivity", sensitivity);
        setField(entity, "requiresManualReview", sensitivity != PermissionSensitivity.NORMAL);
        setField(entity, "isActive", true);
        return entity;
    }

    private static void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read-only lookup entities (PermissionCatalogEntity, PermissionAppTypeRuleEntity) only have
     * a package-private no-arg constructor — rows come from the V010 seed migration, never from
     * application code. Reflection here keeps that constructor private to the entity package
     * instead of widening it just so this cross-package test can call `new`.
     */
    private static <T> T newInstance(Class<T> type) {
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- requestPermission() ----

    @Test
    void requestForcesPendingReviewWhenNoPriorApprovedVersionExistsEvenForNormalSensitivity() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "STORAGE", PermissionSensitivity.NORMAL);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("STORAGE")).thenReturn(Optional.of(permission));
        when(versionPermissions.findByVersionIdAndPermissionId(versionId, permission.getId())).thenReturn(Optional.empty());
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.MINIAPP)).thenReturn(Optional.empty());
        when(versions.findTopByAppIdAndStatusOrderByVersionCodeDesc(appId, VersionStatus.APPROVED)).thenReturn(Optional.empty());
        when(versionPermissions.save(any(AppVersionPermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().requestPermission(appId, versionId, new RequestPermissionRequest("STORAGE", "Lưu file tải xuống của người dùng"));

        assertThat(response.status()).isEqualTo(PermissionRequestStatus.PENDING_REVIEW);
        assertThat(response.isEscalation()).isTrue();
    }

    @Test
    void requestAutoApprovesNormalPermissionAlreadyPresentOnLatestApprovedVersion() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "STORAGE", PermissionSensitivity.NORMAL);
        var baseVersion = AppVersionEntity.create(appId, partnerId, 1, "0.9.0", "Display", "com.example.pkg", null, null, List.of());
        var basePermission = AppVersionPermissionEntity.request(baseVersion.getId(), permission.getId(), "Đã duyệt trước đó",
                PermissionSensitivity.NORMAL, PermissionRequestStatus.APPROVED, false);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("STORAGE")).thenReturn(Optional.of(permission));
        when(versionPermissions.findByVersionIdAndPermissionId(versionId, permission.getId())).thenReturn(Optional.empty());
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.MINIAPP)).thenReturn(Optional.empty());
        when(versions.findTopByAppIdAndStatusOrderByVersionCodeDesc(appId, VersionStatus.APPROVED)).thenReturn(Optional.of(baseVersion));
        when(versionPermissions.findByVersionIdAndStatus(baseVersion.getId(), PermissionRequestStatus.APPROVED))
                .thenReturn(List.of(basePermission));
        when(versionPermissions.save(any(AppVersionPermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().requestPermission(appId, versionId, new RequestPermissionRequest("STORAGE", "Lưu file tải xuống của người dùng"));

        assertThat(response.status()).isEqualTo(PermissionRequestStatus.AUTO_APPROVED);
        assertThat(response.isEscalation()).isFalse();
    }

    @Test
    void requestIsBlockedWhenAppTypeRuleDeniesThePermissionRegardlessOfSensitivity() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.WEBAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var denyRule = newInstance(PermissionAppTypeRuleEntity.class);
        setField(denyRule, "effect", RuleEffect.DENY);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("CAMERA")).thenReturn(Optional.of(permission));
        when(versionPermissions.findByVersionIdAndPermissionId(versionId, permission.getId())).thenReturn(Optional.empty());
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.WEBAPP)).thenReturn(Optional.of(denyRule));
        when(versions.findTopByAppIdAndStatusOrderByVersionCodeDesc(appId, VersionStatus.APPROVED)).thenReturn(Optional.empty());
        when(versionPermissions.save(any(AppVersionPermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().requestPermission(appId, versionId, new RequestPermissionRequest("CAMERA", "Quét mã QR để thanh toán"));

        assertThat(response.status()).isEqualTo(PermissionRequestStatus.BLOCKED);
    }

    @Test
    void requestThrowsAlreadyRequestedWhenPermissionIsDuplicatedOnTheSameVersion() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "STORAGE", PermissionSensitivity.NORMAL);
        var existing = AppVersionPermissionEntity.request(versionId, permission.getId(), "đã có rồi",
                PermissionSensitivity.NORMAL, PermissionRequestStatus.AUTO_APPROVED, false);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("STORAGE")).thenReturn(Optional.of(permission));
        when(versionPermissions.findByVersionIdAndPermissionId(versionId, permission.getId())).thenReturn(Optional.of(existing));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestPermission(appId, versionId,
                new RequestPermissionRequest("STORAGE", "Lưu file tải xuống của người dùng")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_ALREADY_REQUESTED);
    }

    @Test
    void requestThrowsCatalogNotFoundForAnUnknownPermissionCode() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("UNKNOWN")).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestPermission(appId, versionId,
                new RequestPermissionRequest("UNKNOWN", "Lý do bất kỳ đủ hai mươi ký tự")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_CATALOG_NOT_FOUND);
    }

    @Test
    void requestThrowsWhenVersionIsNotInAnEditableState() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);
        version.submit();
        version.approve();

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestPermission(appId, versionId,
                new RequestPermissionRequest("STORAGE", "Lưu file tải xuống của người dùng")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_NOT_EDITABLE);
    }

    // ---- decide() ----

    @Test
    void decideApprovesAPendingReviewRequest() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var request = AppVersionPermissionEntity.request(versionId, permission.getId(), "Quét mã QR để thanh toán",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));
        when(catalog.findById(permission.getId())).thenReturn(Optional.of(permission));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().decide(appId, versionId, request.getId(), new DecidePermissionRequest(PermissionDecisionType.APPROVE, null));

        assertThat(response.status()).isEqualTo(PermissionRequestStatus.APPROVED);
    }

    @Test
    void decideThrowsWhenRejectHasNoReason() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var request = AppVersionPermissionEntity.request(versionId, permission.getId(), "Quét mã QR để thanh toán",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.decide(appId, versionId, request.getId(),
                new DecidePermissionRequest(PermissionDecisionType.REJECT, "  ")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_DECISION_REASON_REQUIRED);
    }

    @Test
    void decideRejectsWithReason() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var request = AppVersionPermissionEntity.request(versionId, permission.getId(), "Quét mã QR để thanh toán",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));
        when(catalog.findById(permission.getId())).thenReturn(Optional.of(permission));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().decide(appId, versionId, request.getId(),
                new DecidePermissionRequest(PermissionDecisionType.REJECT, "Không phù hợp mục đích ứng dụng"));

        assertThat(response.status()).isEqualTo(PermissionRequestStatus.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Không phù hợp mục đích ứng dụng");
    }

    // ---- remove() ----

    @Test
    void removeDeletesTheChildPermissionEventsBeforeDeletingTheParentRequest() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "STORAGE", PermissionSensitivity.NORMAL);
        var request = AppVersionPermissionEntity.request(versionId, permission.getId(), "Lưu file tải xuống của người dùng",
                PermissionSensitivity.NORMAL, PermissionRequestStatus.AUTO_APPROVED, false);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));

        newService().remove(appId, versionId, request.getId());

        InOrder inOrder = inOrder(events, versionPermissions);
        inOrder.verify(events).deleteByAppVersionPermissionId(request.getId());
        inOrder.verify(events).flush();
        inOrder.verify(versionPermissions).delete(request);
    }

    @Test
    void removeThrowsWhenVersionIsNotInAnEditableState() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        version.submit();
        version.approve();

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.remove(appId, versionId, UUID.randomUUID()));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_NOT_EDITABLE);
    }

    // ---- history() (PC-07) ----

    @Test
    void historyReturnsEventsForThePermissionRequestInChronologicalOrder() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var request = AppVersionPermissionEntity.request(versionId, permission.getId(), "Quét mã QR để thanh toán",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);
        var requestedEvent = PermissionEventEntity.create(request.getId(), "REQUESTED", UUID.randomUUID(), "note-1");
        var decidedEvent = PermissionEventEntity.create(request.getId(), "DECIDED", UUID.randomUUID(), "note-2");

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));
        when(events.findByAppVersionPermissionIdOrderByCreatedAtAsc(request.getId()))
                .thenReturn(List.of(requestedEvent, decidedEvent));

        var history = newService().history(appId, versionId, request.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).eventType()).isEqualTo("REQUESTED");
        assertThat(history.get(1).eventType()).isEqualTo("DECIDED");
    }

    @Test
    void historyThrowsWhenPermissionRequestDoesNotBelongToTheVersion() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var otherVersionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var request = AppVersionPermissionEntity.request(otherVersionId, permission.getId(), "Quét mã QR để thanh toán",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionPermissions.findById(request.getId())).thenReturn(Optional.of(request));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.history(appId, versionId, request.getId()));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_REQUEST_NOT_FOUND);
    }

    // ---- catalog admin CRUD (closes the M3 "Platform Admin: Full" gap) ----

    @Test
    void createCatalogEntryPersistsANewActivePermission() {
        when(catalog.existsByCode("BLUETOOTH")).thenReturn(false);
        when(catalog.save(any(PermissionCatalogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().createCatalogEntry(new CreatePermissionCatalogRequest("BLUETOOTH", "Bluetooth",
                PermissionSensitivity.NORMAL, false));

        assertThat(response.code()).isEqualTo("BLUETOOTH");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createCatalogEntryThrowsOnDuplicateCode() {
        when(catalog.existsByCode("CAMERA")).thenReturn(true);

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.createCatalogEntry(
                new CreatePermissionCatalogRequest("CAMERA", "Camera", PermissionSensitivity.DANGEROUS, true)));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_CATALOG_CODE_DUPLICATE);
    }

    @Test
    void updateCatalogEntryChangesFieldsAndCanDeactivate() {
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        when(catalog.findById(permission.getId())).thenReturn(Optional.of(permission));

        var response = newService().updateCatalogEntry(permission.getId(),
                new UpdatePermissionCatalogRequest("Camera (updated)", PermissionSensitivity.SIGNATURE, true, false));

        assertThat(response.displayName()).isEqualTo("Camera (updated)");
        assertThat(response.sensitivity()).isEqualTo(PermissionSensitivity.SIGNATURE);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void updateCatalogEntryThrowsWhenNotFound() {
        var id = UUID.randomUUID();
        when(catalog.findById(id)).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.updateCatalogEntry(id,
                new UpdatePermissionCatalogRequest("x", PermissionSensitivity.NORMAL, false, true)));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_CATALOG_NOT_FOUND);
    }

    @Test
    void upsertAppTypeRuleCreatesANewRuleWhenNoneExistsForThatAppType() {
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        when(catalog.findById(permission.getId())).thenReturn(Optional.of(permission));
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.WEBAPP)).thenReturn(Optional.empty());
        when(rules.save(any(PermissionAppTypeRuleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().upsertAppTypeRule(permission.getId(), ApplicationType.WEBAPP,
                new UpsertAppTypeRuleRequest(RuleEffect.DENY, "Không truy cập phần cứng native"));

        assertThat(response.effect()).isEqualTo(RuleEffect.DENY);
        assertThat(response.appType()).isEqualTo(ApplicationType.WEBAPP);
    }

    @Test
    void upsertAppTypeRuleUpdatesTheExistingRuleForThatAppType() {
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var existingRule = PermissionAppTypeRuleEntity.create(permission.getId(), ApplicationType.WEBAPP, RuleEffect.DENY, "cũ");
        when(catalog.findById(permission.getId())).thenReturn(Optional.of(permission));
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.WEBAPP)).thenReturn(Optional.of(existingRule));

        var response = newService().upsertAppTypeRule(permission.getId(), ApplicationType.WEBAPP,
                new UpsertAppTypeRuleRequest(RuleEffect.CONDITIONAL, "mới"));

        assertThat(response.effect()).isEqualTo(RuleEffect.CONDITIONAL);
        assertThat(response.reason()).isEqualTo("mới");
    }

    @Test
    void deleteAppTypeRuleRemovesTheExistingRule() {
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        var existingRule = PermissionAppTypeRuleEntity.create(permission.getId(), ApplicationType.WEBAPP, RuleEffect.DENY, "cũ");
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.WEBAPP)).thenReturn(Optional.of(existingRule));

        newService().deleteAppTypeRule(permission.getId(), ApplicationType.WEBAPP);

        org.mockito.Mockito.verify(rules).delete(existingRule);
    }

    @Test
    void deleteAppTypeRuleThrowsWhenNoRuleExistsForThatAppType() {
        var permission = catalogEntry(UUID.randomUUID(), "CAMERA", PermissionSensitivity.DANGEROUS);
        when(rules.findByPermissionIdAndAppType(permission.getId(), ApplicationType.WEBAPP)).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.deleteAppTypeRule(permission.getId(), ApplicationType.WEBAPP));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_APP_TYPE_RULE_NOT_FOUND);
    }
}
