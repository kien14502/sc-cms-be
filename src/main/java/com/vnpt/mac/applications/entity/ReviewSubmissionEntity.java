package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_submissions")
public class ReviewSubmissionEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "review_round", nullable = false)
    private int reviewRound;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ReviewSubmissionEntity() {}

    public static ReviewSubmissionEntity create(UUID versionId, UUID partnerId, int reviewRound, UUID submittedBy) {
        var entity = new ReviewSubmissionEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.partnerId = partnerId;
        entity.reviewRound = reviewRound;
        entity.status = SubmissionStatus.PENDING;
        entity.submittedBy = submittedBy;
        entity.submittedAt = Instant.now();
        return entity;
    }

    public void markDecided(SubmissionStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public UUID getPartnerId() { return partnerId; }
    public int getReviewRound() { return reviewRound; }
    public SubmissionStatus getStatus() { return status; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
}
