package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.CapabilityDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CapabilityService (M4 capability domain, mirrors PermissionService's shape per
 * MAC_Design.md §7 note #2: "Partner Dev khai báo → Platform Admin duyệt (đồng bộ với M3)").
 * Unlike Permission, Capability has no sensitivity tiers or escalation detection — the design
 * table (§2.3) only lists "Validate" for System, not "Classify, detect escalation" — so every
 * non-blocked request goes to PENDING_REVIEW; there is no AUTO_APPROVED status. Capability
 * approval is also not part of the version-APPROVED gate (§3.3 only lists app_version_permissions,
 * Public Intent, and validation findings), so unlike Permission this is not wired into ReviewService.
 */
@ExtendWith(MockitoExtension.class)
class CapabilityServiceTest {

    @Mock ApplicationRepository applications;
    @Mock VersionService versionService;
    @Mock CapabilityCatalogRepository catalog;
    @Mock AppVersionCapabilityRepository versionCapabilities;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private CapabilityService newService() {
        return new CapabilityService(applications, versionService, catalog, versionCapabilities, currentUser, audit);
    }

    private AppVersionEntity draftVersion(UUID appId, UUID partnerId) {
        return AppVersionEntity.create(appId, partnerId, 1, "1.0.0", "Display", "com.example.pkg", null, null, List.of());
    }

    // ---- catalog ----

    @Test
    void createCatalogEntryPersistsANewActiveCapability() {
        when(catalog.existsByCode("PUSH_NOTIFICATION")).thenReturn(false);
        when(catalog.save(any(CapabilityCatalogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().createCatalogEntry(new CreateCapabilityCatalogRequest("PUSH_NOTIFICATION",
                "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP, ApplicationType.WEBAPP)));

        assertThat(response.code()).isEqualTo("PUSH_NOTIFICATION");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createCatalogEntryThrowsOnDuplicateCode() {
        when(catalog.existsByCode("PUSH_NOTIFICATION")).thenReturn(true);

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.createCatalogEntry(
                new CreateCapabilityCatalogRequest("PUSH_NOTIFICATION", "x", List.of(ApplicationType.MINIAPP))));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_CATALOG_CODE_DUPLICATE);
    }

    @Test
    void updateCatalogEntryChangesFieldsAndCanDeactivate() {
        var capability = CapabilityCatalogEntity.create("DEEP_LINK", "Deep link", List.of(ApplicationType.MINIAPP));
        when(catalog.findById(capability.getId())).thenReturn(Optional.of(capability));

        var response = newService().updateCatalogEntry(capability.getId(), new UpdateCapabilityCatalogRequest(
                "Deep Link", List.of(ApplicationType.MINIAPP, ApplicationType.FEATURE_APP), false));

        assertThat(response.displayName()).isEqualTo("Deep Link");
        assertThat(response.allowedAppTypes()).containsExactly(ApplicationType.MINIAPP, ApplicationType.FEATURE_APP);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void updateCatalogEntryThrowsWhenNotFound() {
        var id = UUID.randomUUID();
        when(catalog.findById(id)).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.updateCatalogEntry(id,
                new UpdateCapabilityCatalogRequest("x", List.of(ApplicationType.MINIAPP), true)));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_CATALOG_NOT_FOUND);
    }

    // ---- requestCapability() ----

    @Test
    void requestIsPendingReviewWhenAppTypeIsAllowed() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("PUSH_NOTIFICATION")).thenReturn(Optional.of(capability));
        when(versionCapabilities.findByVersionIdAndCapabilityId(versionId, capability.getId())).thenReturn(Optional.empty());
        when(versionCapabilities.save(any(AppVersionCapabilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().requestCapability(appId, versionId, new RequestCapabilityRequest("PUSH_NOTIFICATION"));

        assertThat(response.status()).isEqualTo(CapabilityRequestStatus.PENDING_REVIEW);
    }

    @Test
    void requestIsBlockedWhenAppTypeIsNotInTheAllowedList() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.WEBAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("BACKGROUND_LOCATION", "Chạy nền lấy vị trí", List.of(ApplicationType.MINIAPP));

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("BACKGROUND_LOCATION")).thenReturn(Optional.of(capability));
        when(versionCapabilities.findByVersionIdAndCapabilityId(versionId, capability.getId())).thenReturn(Optional.empty());
        when(versionCapabilities.save(any(AppVersionCapabilityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = newService().requestCapability(appId, versionId, new RequestCapabilityRequest("BACKGROUND_LOCATION"));

        assertThat(response.status()).isEqualTo(CapabilityRequestStatus.BLOCKED);
    }

    @Test
    void requestThrowsAlreadyRequestedWhenCapabilityIsDuplicatedOnTheSameVersion() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));
        var existing = AppVersionCapabilityEntity.request(versionId, capability.getId(), CapabilityRequestStatus.PENDING_REVIEW);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("PUSH_NOTIFICATION")).thenReturn(Optional.of(capability));
        when(versionCapabilities.findByVersionIdAndCapabilityId(versionId, capability.getId())).thenReturn(Optional.of(existing));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestCapability(appId, versionId,
                new RequestCapabilityRequest("PUSH_NOTIFICATION")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_ALREADY_REQUESTED);
    }

    @Test
    void requestThrowsCatalogNotFoundForAnUnknownCapabilityCode() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(catalog.findByCodeAndIsActiveTrue("UNKNOWN")).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestCapability(appId, versionId,
                new RequestCapabilityRequest("UNKNOWN")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_CATALOG_NOT_FOUND);
    }

    @Test
    void requestThrowsWhenVersionIsNotInAnEditableState() {
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var appId = app.getId();
        var versionId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        version.submit();
        version.approve();

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.requestCapability(appId, versionId,
                new RequestCapabilityRequest("PUSH_NOTIFICATION")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_NOT_EDITABLE);
    }

    // ---- decide() ----

    @Test
    void decideApprovesAPendingReviewRequest() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));
        var request = AppVersionCapabilityEntity.request(versionId, capability.getId(), CapabilityRequestStatus.PENDING_REVIEW);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionCapabilities.findById(request.getId())).thenReturn(Optional.of(request));
        when(catalog.findById(capability.getId())).thenReturn(Optional.of(capability));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().decide(appId, versionId, request.getId(), new DecideCapabilityRequest(CapabilityDecisionType.APPROVE, null));

        assertThat(response.status()).isEqualTo(CapabilityRequestStatus.APPROVED);
    }

    @Test
    void decideThrowsWhenRejectHasNoReason() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));
        var request = AppVersionCapabilityEntity.request(versionId, capability.getId(), CapabilityRequestStatus.PENDING_REVIEW);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionCapabilities.findById(request.getId())).thenReturn(Optional.of(request));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.decide(appId, versionId, request.getId(),
                new DecideCapabilityRequest(CapabilityDecisionType.REJECT, "  ")));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_DECISION_REASON_REQUIRED);
    }

    @Test
    void decideRejectsWithReason() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));
        var request = AppVersionCapabilityEntity.request(versionId, capability.getId(), CapabilityRequestStatus.PENDING_REVIEW);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionCapabilities.findById(request.getId())).thenReturn(Optional.of(request));
        when(catalog.findById(capability.getId())).thenReturn(Optional.of(capability));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().decide(appId, versionId, request.getId(),
                new DecideCapabilityRequest(CapabilityDecisionType.REJECT, "Không phù hợp app type"));

        assertThat(response.status()).isEqualTo(CapabilityRequestStatus.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Không phù hợp app type");
    }

    // ---- remove() ----

    @Test
    void removeDeletesTheRequestWhenVersionIsEditable() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId);
        var capability = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy", List.of(ApplicationType.MINIAPP));
        var request = AppVersionCapabilityEntity.request(versionId, capability.getId(), CapabilityRequestStatus.PENDING_REVIEW);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(versionCapabilities.findById(request.getId())).thenReturn(Optional.of(request));

        newService().remove(appId, versionId, request.getId());

        org.mockito.Mockito.verify(versionCapabilities).delete(request);
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
}
