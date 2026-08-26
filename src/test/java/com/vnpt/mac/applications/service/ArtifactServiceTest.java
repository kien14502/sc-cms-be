package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ArtifactDtos.ArtifactResponse;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.ArtifactKind;
import com.vnpt.mac.applications.entity.VersionArtifactEntity;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.applications.repository.ValidationFindingRepository;
import com.vnpt.mac.applications.repository.ValidationRunRepository;
import com.vnpt.mac.applications.repository.VersionArtifactRepository;
import com.vnpt.mac.applications.repository.VersionModuleConfigRepository;
import com.vnpt.mac.applications.repository.VersionWebappConfigRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.config.StorageProperties;
import com.vnpt.mac.security.CurrentUser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Regression test for the delete-then-insert ordering bug found in code review of task 8:
 * Hibernate's flush action queue always runs INSERTs before DELETEs regardless of Java call
 * order, so re-uploading an artifact to a version that already has one would attempt to INSERT
 * the new row (version_id unique) before the old row's DELETE physically lands, violating
 * uq_version_artifacts_version. The fix calls artifacts.flush() between the delete and the
 * subsequent save so the DELETE is forced to the DB first.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock ApplicationRepository applications;
    @Mock VersionService versionService;
    @Mock VersionArtifactRepository artifacts;
    @Mock VersionWebappConfigRepository webappConfigs;
    @Mock VersionModuleConfigRepository moduleConfigs;
    @Mock ValidationRunRepository runs;
    @Mock ValidationFindingRepository findings;
    @Mock ArtifactStorageService storage;
    @Mock ManifestValidationService validation;
    @Mock CurrentUser currentUser;
    @Mock AuditService audit;

    @Test
    void reUploadFlushesTheDeleteOfTheExistingArtifactBeforeSavingTheNewOne() {
        var appId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();

        var app = ApplicationEntity.create(partnerId, ApplicationType.MINIAPP);
        var version = AppVersionEntity.create(appId, partnerId, 1, "1.0.0", "Display",
                "com.example.pkg", null, null, List.of());
        var existing = VersionArtifactEntity.create(versionId, ArtifactKind.ZIP, "old/key.zip",
                "old.zip", 10L, "old-checksum", null);

        when(applications.findById(appId)).thenReturn(Optional.of(app));
        when(versionService.requireVersion(appId, versionId)).thenReturn(version);
        when(artifacts.findByVersionId(versionId)).thenReturn(Optional.of(existing));
        when(validation.validateMiniApp(any(byte[].class), anyLong()))
                .thenReturn(new ManifestValidationService.ValidationOutcome(true, List.of()));
        when(storage.store(eq(versionId), eq("new.zip"), any(byte[].class)))
                .thenReturn(new ArtifactStorageService.StoredArtifact("new/key.zip", 20L, "new-checksum"));
        when(artifacts.save(any(VersionArtifactEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currentUser.id()).thenReturn(UUID.randomUUID());

        var service = new ArtifactService(applications, versionService, artifacts, webappConfigs,
                moduleConfigs, runs, findings, storage, validation, new StorageProperties("/tmp", 10_000_000L),
                currentUser, audit);

        var file = new MockMultipartFile("file", "new.zip", "application/zip", "new-content".getBytes());

        ArtifactResponse response = service.uploadArtifact(appId, versionId, file);

        assertThat(response.kind()).isEqualTo(ArtifactKind.ZIP);
        assertThat(response.originalFilename()).isEqualTo("new.zip");
        assertThat(response.checksumSha256()).isEqualTo("new-checksum");

        // The core regression check: the old row's delete must be flushed to the DB before the
        // new row's save is attempted, otherwise Hibernate's insert-before-delete flush ordering
        // would violate the unique index on version_id when this transaction eventually flushes.
        InOrder inOrder = inOrder(artifacts);
        inOrder.verify(artifacts).delete(existing);
        inOrder.verify(artifacts).flush();
        inOrder.verify(artifacts).save(any(VersionArtifactEntity.class));
    }
}
