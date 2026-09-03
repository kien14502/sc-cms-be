package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, UUID> {
    Page<AppVersionEntity> findByAppId(UUID appId, Pageable pageable);
    Page<AppVersionEntity> findByAppIdAndStatus(UUID appId, VersionStatus status, Pageable pageable);
    Page<AppVersionEntity> findByStatus(VersionStatus status, Pageable pageable);
    Optional<AppVersionEntity> findTopByAppIdOrderByVersionCodeDesc(UUID appId);
    Optional<AppVersionEntity> findTopByAppIdAndStatusOrderByVersionCodeDesc(UUID appId, VersionStatus status);
    long countByAppId(UUID appId);
    boolean existsByAppIdAndStatus(UUID appId, VersionStatus status);
}
