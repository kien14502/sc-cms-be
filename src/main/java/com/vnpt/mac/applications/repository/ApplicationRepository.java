package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
    boolean existsByIdAndPartnerId(UUID id, UUID partnerId);
    long countByPartnerIdAndStatus(UUID partnerId, ApplicationStatus status);
}
