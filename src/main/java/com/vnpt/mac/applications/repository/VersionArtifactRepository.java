package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionArtifactEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionArtifactRepository extends JpaRepository<VersionArtifactEntity, UUID> {
    Optional<VersionArtifactEntity> findByVersionId(UUID versionId);
}
