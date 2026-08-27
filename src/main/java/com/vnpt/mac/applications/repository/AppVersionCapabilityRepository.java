package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppVersionCapabilityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionCapabilityRepository extends JpaRepository<AppVersionCapabilityEntity, UUID> {
    List<AppVersionCapabilityEntity> findByVersionIdOrderByCreatedAtAsc(UUID versionId);
    Optional<AppVersionCapabilityEntity> findByVersionIdAndCapabilityId(UUID versionId, UUID capabilityId);
}
