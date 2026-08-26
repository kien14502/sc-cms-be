package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_decisions")
public class ReviewDecisionEntity {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecisionType decision;

    @Column
    private String feedback;

    @Column(name = "decided_by", nullable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReviewDecisionEntity() {}

    public static ReviewDecisionEntity create(UUID submissionId, ReviewDecisionType decision, String feedback, UUID decidedBy) {
        var entity = new ReviewDecisionEntity();
        entity.id = UUID.randomUUID();
        entity.submissionId = submissionId;
        entity.decision = decision;
        entity.feedback = feedback;
        entity.decidedBy = decidedBy;
        entity.decidedAt = Instant.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public ReviewDecisionType getDecision() { return decision; }
    public String getFeedback() { return feedback; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
