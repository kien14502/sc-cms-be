package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewDecisionRequest(@NotNull ReviewDecisionType decision, @Size(max = 1000) String feedback) {
    }

    public record ReviewDecisionResponse(UUID id, ReviewDecisionType decision, String feedback, UUID decidedBy, Instant decidedAt) {
        public static ReviewDecisionResponse from(ReviewDecisionEntity e) {
            return new ReviewDecisionResponse(e.getId(), e.getDecision(), e.getFeedback(), e.getDecidedBy(), e.getDecidedAt());
        }
    }

    public record ReviewSubmissionResponse(UUID id, int reviewRound, SubmissionStatus status, UUID submittedBy,
                                           Instant submittedAt, ReviewDecisionResponse decision) {
        public static ReviewSubmissionResponse from(ReviewSubmissionEntity e, ReviewDecisionResponse decision) {
            return new ReviewSubmissionResponse(e.getId(), e.getReviewRound(), e.getStatus(), e.getSubmittedBy(), e.getSubmittedAt(), decision);
        }
    }
}
