package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.PermissionEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionEventRepository extends JpaRepository<PermissionEventEntity, UUID> {
    List<PermissionEventEntity> findByAppVersionPermissionIdOrderByCreatedAtAsc(UUID appVersionPermissionId);
    void deleteByAppVersionPermissionId(UUID appVersionPermissionId);
}
