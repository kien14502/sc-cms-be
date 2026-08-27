package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppVersionPermissionEntity;
import com.vnpt.mac.applications.entity.PermissionRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionPermissionRepository extends JpaRepository<AppVersionPermissionEntity, UUID> {
    List<AppVersionPermissionEntity> findByVersionIdOrderByCreatedAtAsc(UUID versionId);
    Optional<AppVersionPermissionEntity> findByVersionIdAndPermissionId(UUID versionId, UUID permissionId);
    List<AppVersionPermissionEntity> findByVersionIdAndStatus(UUID versionId, PermissionRequestStatus status);
    boolean existsByVersionIdAndStatusIn(UUID versionId, List<PermissionRequestStatus> statuses);
}
