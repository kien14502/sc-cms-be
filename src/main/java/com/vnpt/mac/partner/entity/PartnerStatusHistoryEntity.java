package com.vnpt.mac.partner.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_status_history")
public class PartnerStatusHistoryEntity {
    @Id private UUID id;
    @Column(name = "partner_id", nullable = false) private UUID partnerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status") private PartnerStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false) private PartnerStatus toStatus;
    private String reason;
    @Column(name = "changed_by") private UUID changedBy;
    @Column(name = "changed_at", nullable = false) private Instant changedAt;
    protected PartnerStatusHistoryEntity() {}
    public static PartnerStatusHistoryEntity of(UUID partnerId, PartnerStatus from, PartnerStatus to, String reason, UUID by) {
        var h = new PartnerStatusHistoryEntity(); h.id = UUID.randomUUID(); h.partnerId = partnerId;
        h.fromStatus = from; h.toStatus = to; h.reason = reason; h.changedBy = by; h.changedAt = Instant.now(); return h;
    }
    public UUID getId() { return id; }
    public UUID getPartnerId() { return partnerId; }
    public PartnerStatus getFromStatus() { return fromStatus; }
    public PartnerStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
