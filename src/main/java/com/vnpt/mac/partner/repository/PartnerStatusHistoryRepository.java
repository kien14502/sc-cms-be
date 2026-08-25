package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.PartnerStatusHistoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PartnerStatusHistoryRepository extends JpaRepository<PartnerStatusHistoryEntity, UUID> {
    List<PartnerStatusHistoryEntity> findByPartnerIdOrderByChangedAtDesc(UUID partnerId);
}
