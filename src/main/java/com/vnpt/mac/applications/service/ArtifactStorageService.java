package com.vnpt.mac.applications.service;

import com.vnpt.mac.config.StorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ArtifactStorageService {
    private final StorageProperties properties;

    public ArtifactStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    public record StoredArtifact(String storageKey, long sizeBytes, String checksumSha256) {}

    public StoredArtifact store(UUID versionId, String originalFilename, byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var checksum = HexFormat.of().formatHex(digest.digest(content));
            var relativeKey = versionId + "/" + UUID.randomUUID() + "-" + sanitize(originalFilename);
            var target = Path.of(properties.artifactsDir()).resolve(relativeKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new StoredArtifact(relativeKey, content.length, checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Không lưu được artifact", e);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(Path.of(properties.artifactsDir()).resolve(storageKey));
        } catch (IOException ignored) {
        }
    }

    private String sanitize(String filename) {
        return filename == null ? "artifact" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
