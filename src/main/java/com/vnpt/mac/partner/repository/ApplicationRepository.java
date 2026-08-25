package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.ApplicationEntity;
import com.vnpt.mac.partner.entity.ApplicationStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
    boolean existsByIdAndPartnerId(UUID id, UUID partnerId);
    long countByPartnerIdAndStatus(UUID partnerId, ApplicationStatus status);
}
