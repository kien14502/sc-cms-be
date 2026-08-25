package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.AppDeveloperAssignmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppDeveloperAssignmentRepository extends JpaRepository<AppDeveloperAssignmentEntity, UUID> {
    List<AppDeveloperAssignmentEntity> findByAppIdAndRevokedAtIsNull(UUID appId);
    Optional<AppDeveloperAssignmentEntity> findByAppIdAndUserIdAndRevokedAtIsNull(UUID appId, UUID userId);
}
