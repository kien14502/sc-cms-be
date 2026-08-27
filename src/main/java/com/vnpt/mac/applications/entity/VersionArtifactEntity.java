package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_artifacts")
public class VersionArtifactEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArtifactKind kind;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "signature_fingerprint")
    private String signatureFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VersionArtifactEntity() {}

    public static VersionArtifactEntity create(UUID versionId, ArtifactKind kind, String storageKey,
                                                String originalFilename, long sizeBytes, String checksumSha256,
                                                String signatureFingerprint) {
        var entity = new VersionArtifactEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.kind = kind;
        entity.storageKey = storageKey;
        entity.originalFilename = originalFilename;
        entity.sizeBytes = sizeBytes;
        entity.checksumSha256 = checksumSha256;
        entity.signatureFingerprint = signatureFingerprint;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public ArtifactKind getKind() { return kind; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getSignatureFingerprint() { return signatureFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
