package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.PartnerEntity;
import com.vnpt.mac.partner.entity.PartnerStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<PartnerEntity, UUID> {
    boolean existsByTaxCodeIgnoreCase(String taxCode);
    Page<PartnerEntity> findByStatus(PartnerStatus status, Pageable pageable);
    Page<PartnerEntity> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    Page<PartnerEntity> findByStatusAndNameContainingIgnoreCase(PartnerStatus status, String keyword, Pageable pageable);
}
