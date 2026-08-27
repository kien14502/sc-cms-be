package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.CapabilityCatalogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapabilityCatalogRepository extends JpaRepository<CapabilityCatalogEntity, UUID> {
    List<CapabilityCatalogEntity> findByIsActiveTrue();
    List<CapabilityCatalogEntity> findAllByOrderByCodeAsc();
    Optional<CapabilityCatalogEntity> findByCodeAndIsActiveTrue(String code);
    boolean existsByCode(String code);
}
