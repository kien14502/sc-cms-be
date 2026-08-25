package com.vnpt.mac.partner.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partners")
public class PartnerEntity extends BaseAuditEntity {
    @Id
    private UUID id;

    @Column(name = "partner_code", nullable = false, unique = true, length = 50)
    private String partnerCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "tax_code", unique = true, length = 50)
    private String taxCode;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerStatus status;

    @Column(name = "suspend_reason")
    private String suspendReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    protected PartnerEntity() {}

    public static PartnerEntity create(String partnerCode, String name, String taxCode,
                                       String contactEmail, String contactPhone, boolean active) {
        var entity = new PartnerEntity();
        entity.id = UUID.randomUUID();
        entity.partnerCode = partnerCode;
        entity.name = name.trim();
        entity.taxCode = normalizeNullable(taxCode);
        entity.contactEmail = contactEmail.trim().toLowerCase();
        entity.contactPhone = normalizeNullable(contactPhone);
        entity.status = active ? PartnerStatus.ACTIVE : PartnerStatus.PENDING_APPROVAL;
        return entity;
    }

    public void update(String name, String contactEmail, String contactPhone) {
        this.name = name.trim();
        this.contactEmail = contactEmail.trim().toLowerCase();
        this.contactPhone = normalizeNullable(contactPhone);
    }

    public void approve() {
        requireStatus(PartnerStatus.PENDING_APPROVAL);
        status = PartnerStatus.ACTIVE;
    }

    public void reject() {
        requireStatus(PartnerStatus.PENDING_APPROVAL);
        status = PartnerStatus.REJECTED;
    }

    public void suspend(String reason) {
        requireStatus(PartnerStatus.ACTIVE);
        status = PartnerStatus.SUSPENDED;
        suspendReason = reason.trim();
        suspendedAt = Instant.now();
    }

    public void unsuspend() {
        requireStatus(PartnerStatus.SUSPENDED);
        status = PartnerStatus.ACTIVE;
        suspendReason = null;
        suspendedAt = null;
    }

    public void requireActive() {
        if (status != PartnerStatus.ACTIVE) throw new BusinessException(ErrorCode.PARTNER_NOT_ACTIVE);
    }

    private void requireStatus(PartnerStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.PARTNER_STATUS_INVALID,
                "Không thể chuyển Partner từ " + status);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getPartnerCode() { return partnerCode; }
    public String getName() { return name; }
    public String getTaxCode() { return taxCode; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public PartnerStatus getStatus() { return status; }
    public String getSuspendReason() { return suspendReason; }
    public Instant getSuspendedAt() { return suspendedAt; }
}
