package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.PermissionCatalogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionCatalogRepository extends JpaRepository<PermissionCatalogEntity, UUID> {
    List<PermissionCatalogEntity> findByIsActiveTrue();
    List<PermissionCatalogEntity> findAllByOrderByCodeAsc();
    Optional<PermissionCatalogEntity> findByCodeAndIsActiveTrue(String code);
    boolean existsByCode(String code);
}
