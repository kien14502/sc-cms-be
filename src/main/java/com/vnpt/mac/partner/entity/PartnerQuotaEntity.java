package com.vnpt.mac.partner.entity;

import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "partner_quotas")
public class PartnerQuotaEntity extends BaseAuditEntity {
    @Id
    @Column(name = "partner_id")
    private UUID partnerId;
    @Column(name = "max_apps", nullable = false)
    private int maxApps;
    @Column(name = "max_developers", nullable = false)
    private int maxDevelopers;
    @Column(name = "max_concurrent_submissions", nullable = false)
    private int maxConcurrentSubmissions;
    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes;

    protected PartnerQuotaEntity() {}

    public static PartnerQuotaEntity defaults(UUID partnerId) {
        var q = new PartnerQuotaEntity();
        q.partnerId = partnerId;
        q.update(10, 20, 3, 5L * 1024 * 1024 * 1024);
        return q;
    }

    public void update(int apps, int developers, int submissions, long storageBytes) {
        this.maxApps = apps;
        this.maxDevelopers = developers;
        this.maxConcurrentSubmissions = submissions;
        this.maxStorageBytes = storageBytes;
    }

    public UUID getPartnerId() { return partnerId; }
    public int getMaxApps() { return maxApps; }
    public int getMaxDevelopers() { return maxDevelopers; }
    public int getMaxConcurrentSubmissions() { return maxConcurrentSubmissions; }
    public long getMaxStorageBytes() { return maxStorageBytes; }
}
