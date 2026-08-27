package com.vnpt.mac.applications.service;

import com.vnpt.mac.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ArtifactStorageServiceTest {
    @TempDir Path tempDir;

    @Test void storesFileAndComputesChecksum() throws Exception {
        var service = new ArtifactStorageService(new StorageProperties(tempDir.toString(), 1_000_000L));
        var content = "hello world".getBytes(StandardCharsets.UTF_8);
        var versionId = UUID.randomUUID();

        var stored = service.store(versionId, "app.zip", content);

        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.checksumSha256()).hasSize(64);
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).isEqualTo(content);
    }

    @Test void deleteRemovesTheStoredFile() {
        var service = new ArtifactStorageService(new StorageProperties(tempDir.toString(), 1_000_000L));
        var stored = service.store(UUID.randomUUID(), "app.zip", "data".getBytes(StandardCharsets.UTF_8));

        service.delete(stored.storageKey());

        assertThat(Files.exists(tempDir.resolve(stored.storageKey()))).isFalse();
    }
}
