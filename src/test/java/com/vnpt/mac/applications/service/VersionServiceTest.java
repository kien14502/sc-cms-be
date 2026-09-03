package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import com.vnpt.mac.applications.repository.AppCategoryRepository;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.security.CurrentUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cross-application review queue: reviewers page through GET /api/v1/versions?status=IN_REVIEW
 * instead of visiting every application's own /versions endpoint (no such application-scoped
 * detour exists here — see VersionQueueController).
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock AppVersionRepository versions;
    @Mock ApplicationRepository applications;
    @Mock AppCategoryRepository categories;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private VersionService newService() {
        return new VersionService(versions, applications, categories, currentUser, audit);
    }

    private AppVersionEntity inReviewVersion() {
        var v = AppVersionEntity.create(UUID.randomUUID(), UUID.randomUUID(), 1, "1.0.0", "Display",
                "com.example.pkg", null, null, List.of());
        v.submit();
        return v;
    }

    @Test
    void listAllVersionsFiltersByStatusAcrossApplications() {
        var pageable = PageRequest.of(0, 20);
        var v = inReviewVersion();
        when(versions.findByStatus(VersionStatus.IN_REVIEW, pageable)).thenReturn(new PageImpl<>(List.of(v)));

        Page<?> result = newService().listAllVersions(VersionStatus.IN_REVIEW, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(versions, never()).findAll(pageable);
    }

    @Test
    void listAllVersionsReturnsEveryVersionWhenStatusOmitted() {
        var pageable = PageRequest.of(0, 20);
        var v = inReviewVersion();
        when(versions.findAll(pageable)).thenReturn(new PageImpl<>(List.of(v)));

        Page<?> result = newService().listAllVersions(null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
