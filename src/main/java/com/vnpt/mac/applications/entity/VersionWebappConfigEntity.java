package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_webapp_config")
public class VersionWebappConfigEntity {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "destination_url", nullable = false, length = 500)
    private String destinationUrl;

    @Column(name = "ssl_valid", nullable = false)
    private boolean sslValid;

    @Column(name = "last_health_status")
    private Integer lastHealthStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    protected VersionWebappConfigEntity() {}

    public static VersionWebappConfigEntity create(UUID versionId, String destinationUrl, boolean sslValid) {
        var entity = new VersionWebappConfigEntity();
        entity.versionId = versionId;
        entity.destinationUrl = destinationUrl;
        entity.sslValid = sslValid;
        return entity;
    }

    public void update(String destinationUrl, boolean sslValid) {
        this.destinationUrl = destinationUrl;
        this.sslValid = sslValid;
    }

    public UUID getVersionId() { return versionId; }
    public String getDestinationUrl() { return destinationUrl; }
    public boolean isSslValid() { return sslValid; }
    public Integer getLastHealthStatus() { return lastHealthStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
}
