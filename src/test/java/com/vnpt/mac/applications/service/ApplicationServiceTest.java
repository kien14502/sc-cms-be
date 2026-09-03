package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ApplicationDtos.CreateApplicationRequest;
import com.vnpt.mac.applications.dto.VersionDtos.VersionMetadataFields;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.security.CurrentUser;
import com.vnpt.mac.security.MacPrincipal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * There is no cross-application "versions in review" endpoint (docs/superpowers/plans/2026-08-25
 * -m2-application-version.md never adds one) — reviewers work the queue by listing every
 * application via ApplicationService.list() and then paging into each one's /versions?status=
 * IN_REVIEW. That workaround only works if list() actually grants a REVIEWER cross-partner
 * visibility; this test locks in the scoping rule (@resourceAuth.app already lets REVIEWER read
 * any single app once appId is known, see ResourceAuthorizationService.app()).
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository applications;
    @Mock AppVersionRepository versions;
    @Mock VersionService versionService;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private ApplicationService newService() {
        return new ApplicationService(applications, versions, versionService, currentUser, audit);
    }

    private MacPrincipal principal(UUID partnerId, String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        return new MacPrincipal(UUID.randomUUID(), partnerId, "user@example.com", "hash", granted);
    }

    @Test
    void reviewerWithoutAppReadAllSeesApplicationsAcrossAllPartners() {
        when(currentUser.require()).thenReturn(principal(null, "app.read", "ROLE_REVIEWER"));
        var pageable = PageRequest.of(0, 20);
        when(applications.search(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(Page.empty(pageable));

        newService().list(null, null, null, pageable);

        verify(applications).search(isNull(), isNull(), isNull(), eq(pageable));
    }

    @Test
    void platformAdminWithAppReadAllSeesApplicationsAcrossAllPartners() {
        when(currentUser.require()).thenReturn(principal(null, "app.read.all"));
        var pageable = PageRequest.of(0, 20);
        when(applications.search(isNull(), isNull(), isNull(), eq(pageable))).thenReturn(Page.empty(pageable));

        newService().list(null, null, null, pageable);

        verify(applications).search(isNull(), isNull(), isNull(), eq(pageable));
    }

    @Test
    void partnerUserWithPlainAppReadIsScopedToOwnPartnerOnly() {
        var partnerId = UUID.randomUUID();
        when(currentUser.require()).thenReturn(principal(partnerId, "app.read"));
        var pageable = PageRequest.of(0, 20);
        when(applications.search(eq(partnerId), isNull(), isNull(), eq(pageable))).thenReturn(Page.empty(pageable));

        newService().list(null, null, null, pageable);

        verify(applications).search(eq(partnerId), isNull(), isNull(), eq(pageable));
    }

    @Test
    void userWithNoPartnerAndNoGlobalPermissionGetsEmptyPageInsteadOfEverything() {
        when(currentUser.require()).thenReturn(principal(null, "app.read"));
        var pageable = PageRequest.of(0, 20);

        var result = newService().list(null, null, null, pageable);

        assertThat(result).isEmpty();
        verify(applications, never()).search(any(), any(), any(), any());
    }

    @Test
    void globalUserCanFilterAppListByExplicitPartnerId() {
        var requestedPartnerId = UUID.randomUUID();
        when(currentUser.require()).thenReturn(principal(null, "app.read.all"));
        var pageable = PageRequest.of(0, 20);
        when(applications.search(eq(requestedPartnerId), isNull(), isNull(), eq(pageable))).thenReturn(Page.empty(pageable));

        newService().list(null, null, requestedPartnerId, pageable);

        verify(applications).search(eq(requestedPartnerId), isNull(), isNull(), eq(pageable));
    }

    @Test
    void nonGlobalUserRequestingAnotherPartnersIdIsStillScopedToTheirOwnPartner() {
        var ownPartnerId = UUID.randomUUID();
        var someoneElsesPartnerId = UUID.randomUUID();
        when(currentUser.require()).thenReturn(principal(ownPartnerId, "app.read"));
        var pageable = PageRequest.of(0, 20);
        when(applications.search(eq(ownPartnerId), isNull(), isNull(), eq(pageable))).thenReturn(Page.empty(pageable));

        newService().list(null, null, someoneElsesPartnerId, pageable);

        verify(applications).search(eq(ownPartnerId), isNull(), isNull(), eq(pageable));
        verify(applications, never()).search(eq(someoneElsesPartnerId), any(), any(), any());
    }

    @Test
    void createdApplicationDefaultsToDraftAndIsPartnerScoped() {
        var partnerId = UUID.randomUUID();
        when(currentUser.partnerId()).thenReturn(partnerId);
        var version = AppVersionEntity.create(UUID.randomUUID(), partnerId, 1,
                "1.0.0", "Display", "com.example.pkg", null, null, List.of());
        when(versionService.createInitialVersion(any(), eq(partnerId), any())).thenReturn(version);
        var request = new CreateApplicationRequest(ApplicationType.MINIAPP,
                new VersionMetadataFields("1.0.0", "Display", "com.example.pkg", null, null, List.of(), null));

        var response = newService().create(request);

        assertThat(response.partnerId()).isEqualTo(partnerId);
        assertThat(response.status()).isEqualTo(ApplicationStatus.DRAFT);
    }
}
