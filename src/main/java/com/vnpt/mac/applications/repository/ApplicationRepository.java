package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
    boolean existsByIdAndPartnerId(UUID id, UUID partnerId);
    long countByPartnerIdAndStatus(UUID partnerId, ApplicationStatus status);

    @Query("SELECT a FROM ApplicationEntity a WHERE (:partnerId IS NULL OR a.partnerId = :partnerId) " +
           "AND (:status IS NULL OR a.status = :status) AND (:appType IS NULL OR a.appType = :appType)")
    Page<ApplicationEntity> search(@Param("partnerId") UUID partnerId, @Param("status") ApplicationStatus status,
                                    @Param("appType") ApplicationType appType, Pageable pageable);
}
