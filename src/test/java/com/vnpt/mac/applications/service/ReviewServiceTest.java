package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ReviewDtos.ReviewDecisionRequest;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.ReviewDecisionEntity;
import com.vnpt.mac.applications.entity.ReviewDecisionType;
import com.vnpt.mac.applications.entity.ReviewSubmissionEntity;
import com.vnpt.mac.applications.entity.SubmissionStatus;
import com.vnpt.mac.applications.entity.ValidationRunEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ReviewDecisionRepository;
import com.vnpt.mac.applications.repository.ReviewSubmissionRepository;
import com.vnpt.mac.applications.repository.ValidationRunRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReviewService's core business rules (design spec requirement that the plan
 * dropped, closed in the M2 final-review fix wave): submit() guards on validation state and
 * version state, decide()'s feedback-required rule, and the hadApprovedBefore->activate rule.
 * Mirrors ArtifactServiceTest's mocked-repository pattern: Mockito-only, no Spring context, real
 * domain entities exercised through their own create()/state-machine methods.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock VersionService versionService;
    @Mock ApplicationRepository applications;
    @Mock AppVersionRepository versions;
    @Mock ValidationRunRepository validationRuns;
    @Mock ReviewSubmissionRepository submissions;
    @Mock ReviewDecisionRepository decisions;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    private ReviewService newService() {
        return new ReviewService(versionService, applications, versions, validationRuns, submissions, decisions, currentUser, audit);
    }

    private AppVersionEntity draftVersion(UUID appId, UUID partnerId) {
        return AppVersionEntity.create(appId, partnerId, 1, "1.0.0", "Display", "com.example.pkg", null, null, List.of());
    }

    private AppVersionEntity inReviewVersion(UUID appId, UUID partnerId) {
        var v = draftVersion(appId, partnerId);
        v.submit();
        return v;
    }

    // ---- submit() ----

    @Test
    void submitThrowsArtifactMissingWhenNoValidationRunExists() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)).thenReturn(Optional.empty());

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.submit(appId, versionId));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.ARTIFACT_MISSING);
    }

    @Test
    void submitThrowsValidationFailedWhenLatestRunDidNotPass() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);
        var failedRun = ValidationRunEntity.start(versionId, UUID.randomUUID());
        failedRun.complete(false);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)).thenReturn(Optional.of(failedRun));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.submit(appId, versionId));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void submitSucceedsAndMovesVersionToInReviewWhenValidationPassed() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);
        var passedRun = ValidationRunEntity.start(versionId, UUID.randomUUID());
        passedRun.complete(true);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)).thenReturn(Optional.of(passedRun));
        when(submissions.save(any(ReviewSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().submit(appId, versionId);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        verify(submissions).save(any(ReviewSubmissionEntity.class));
    }

    @Test
    void submitBypassesValidationRunCheckForApp2AppType() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.APP2APP);
        var version = draftVersion(appId, partnerId);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(submissions.save(any(ReviewSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var response = newService().submit(appId, versionId);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(response).isNotNull();
        verify(validationRuns, never()).findTopByVersionIdOrderByStartedAtDesc(any());
    }

    @Test
    void submitThrowsWhenVersionIsNotInAnEditableState() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = draftVersion(appId, partnerId);
        version.submit(); // DRAFT -> IN_REVIEW, no longer editable/submittable
        var passedRun = ValidationRunEntity.start(versionId, UUID.randomUUID());
        passedRun.complete(true);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)).thenReturn(Optional.of(passedRun));

        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.submit(appId, versionId));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_NOT_EDITABLE);
    }

    // ---- decide() ----

    @Test
    void decideThrowsFeedbackRequiredWhenRejectHasNoFeedback() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = inReviewVersion(appId, partnerId);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var request = new ReviewDecisionRequest(ReviewDecisionType.REJECT, null);
        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.decide(appId, versionId, request));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.REVIEW_FEEDBACK_REQUIRED);
    }

    @Test
    void decideThrowsFeedbackRequiredWhenRequestChangesHasBlankFeedback() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = inReviewVersion(appId, partnerId);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var request = new ReviewDecisionRequest(ReviewDecisionType.REQUEST_CHANGES, "   ");
        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.decide(appId, versionId, request));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.REVIEW_FEEDBACK_REQUIRED);
    }

    @Test
    void decideDoesNotRequireFeedbackForApprove() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = inReviewVersion(appId, partnerId);
        var submission = ReviewSubmissionEntity.create(versionId, partnerId, version.getReviewRound(), UUID.randomUUID());
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(submissions.findTopByVersionIdOrderBySubmittedAtDesc(versionId)).thenReturn(Optional.of(submission));
        when(versions.existsByAppIdAndStatus(appId, VersionStatus.APPROVED)).thenReturn(false);
        when(decisions.save(any(ReviewDecisionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var request = new ReviewDecisionRequest(ReviewDecisionType.APPROVE, null);
        var response = newService().decide(appId, versionId, request);

        assertThat(response).isNotNull();
        assertThat(version.getStatus()).isEqualTo(VersionStatus.APPROVED);
    }

    @Test
    void firstApprovalActivatesTheApplication() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = inReviewVersion(appId, partnerId);
        var submission = ReviewSubmissionEntity.create(versionId, partnerId, version.getReviewRound(), UUID.randomUUID());
        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(submissions.findTopByVersionIdOrderBySubmittedAtDesc(versionId)).thenReturn(Optional.of(submission));
        when(versions.existsByAppIdAndStatus(appId, VersionStatus.APPROVED)).thenReturn(false);
        when(decisions.save(any(ReviewDecisionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var request = new ReviewDecisionRequest(ReviewDecisionType.APPROVE, null);
        newService().decide(appId, versionId, request);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.ACTIVE);
        verify(applications).save(app);
    }

    @Test
    void secondApprovalDoesNotReactivateTheApplication() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = inReviewVersion(appId, partnerId);
        var submission = ReviewSubmissionEntity.create(versionId, partnerId, version.getReviewRound(), UUID.randomUUID());

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(submissions.findTopByVersionIdOrderBySubmittedAtDesc(versionId)).thenReturn(Optional.of(submission));
        when(versions.existsByAppIdAndStatus(appId, VersionStatus.APPROVED)).thenReturn(true);
        when(decisions.save(any(ReviewDecisionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var request = new ReviewDecisionRequest(ReviewDecisionType.APPROVE, null);
        newService().decide(appId, versionId, request);

        assertThat(version.getStatus()).isEqualTo(VersionStatus.APPROVED);
        verify(applications, never()).findById(any());
        verify(applications, never()).save(any());
    }

    @Test
    void decideThrowsWhenVersionIsNotInReview() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var version = draftVersion(appId, partnerId); // still DRAFT, never submitted

        when(versionService.requireVersion(appId, versionId)).thenReturn(version);

        var request = new ReviewDecisionRequest(ReviewDecisionType.APPROVE, null);
        var service = newService();
        var ex = assertThrows(BusinessException.class, () -> service.decide(appId, versionId, request));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_STATUS_INVALID);
    }
}
