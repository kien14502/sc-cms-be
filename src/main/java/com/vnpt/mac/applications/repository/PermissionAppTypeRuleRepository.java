package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.entity.PermissionAppTypeRuleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionAppTypeRuleRepository extends JpaRepository<PermissionAppTypeRuleEntity, UUID> {
    Optional<PermissionAppTypeRuleEntity> findByPermissionIdAndAppType(UUID permissionId, ApplicationType appType);
}
