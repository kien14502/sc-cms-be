package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ReviewDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final VersionService versionService;
    private final ApplicationRepository applications;
    private final AppVersionRepository versions;
    private final ValidationRunRepository validationRuns;
    private final ReviewSubmissionRepository submissions;
    private final ReviewDecisionRepository decisions;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ReviewService(VersionService versionService, ApplicationRepository applications, AppVersionRepository versions,
                         ValidationRunRepository validationRuns, ReviewSubmissionRepository submissions,
                         ReviewDecisionRepository decisions, CurrentUser currentUser, AuditService audit) {
        this.versionService = versionService;
        this.applications = applications;
        this.versions = versions;
        this.validationRuns = validationRuns;
        this.submissions = submissions;
        this.decisions = decisions;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ReviewSubmissionResponse submit(UUID appId, UUID versionId) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var version = versionService.requireVersion(appId, versionId);
        if (app.getAppType() != ApplicationType.APP2APP) {
            var latestRun = validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_MISSING, "Chưa upload artifact/cấu hình cho version này"));
            if (!latestRun.passed())
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Còn lỗi validation mức ERROR, không thể submit");
        }
        version.submit();
        var submission = submissions.save(ReviewSubmissionEntity.create(versionId, version.getPartnerId(), version.getReviewRound(), currentUser.id()));
        var response = ReviewSubmissionResponse.from(submission, null);
        audit.log(version.getPartnerId(), "VERSION_SUBMITTED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public ReviewSubmissionResponse decide(UUID appId, UUID versionId, ReviewDecisionRequest r) {
        var version = versionService.requireVersion(appId, versionId);
        if (version.getStatus() != VersionStatus.IN_REVIEW)
            throw new BusinessException(ErrorCode.VERSION_STATUS_INVALID, "Version không ở trạng thái IN_REVIEW");
        if (r.decision() != ReviewDecisionType.APPROVE && (r.feedback() == null || r.feedback().isBlank()))
            throw new BusinessException(ErrorCode.REVIEW_FEEDBACK_REQUIRED);
        var submission = submissions.findTopByVersionIdOrderBySubmittedAtDesc(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_STATUS_INVALID, "Không tìm thấy lượt submit"));
        boolean hadApprovedBefore = versions.existsByAppIdAndStatus(version.getAppId(), VersionStatus.APPROVED);
        var decisionEntity = decisions.save(ReviewDecisionEntity.create(submission.getId(), r.decision(), r.feedback(), currentUser.id()));
        switch (r.decision()) {
            case APPROVE -> {
                version.approve();
                submission.markDecided(SubmissionStatus.APPROVED);
                if (!hadApprovedBefore) {
                    var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
                    app.activate();
                    applications.save(app);
                }
            }
            case REJECT -> {
                version.reject();
                submission.markDecided(SubmissionStatus.REJECTED);
            }
            case REQUEST_CHANGES -> {
                version.requestChanges();
                submission.markDecided(SubmissionStatus.CHANGES_REQUESTED);
            }
        }
        var response = ReviewSubmissionResponse.from(submission, ReviewDecisionResponse.from(decisionEntity));
        audit.log(version.getPartnerId(), "VERSION_REVIEW_DECIDED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReviewSubmissionResponse> history(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        return submissions.findByVersionIdOrderBySubmittedAtAsc(versionId).stream()
                .map(s -> ReviewSubmissionResponse.from(s, decisions.findBySubmissionId(s.getId()).map(ReviewDecisionResponse::from).orElse(null)))
                .toList();
    }
}
