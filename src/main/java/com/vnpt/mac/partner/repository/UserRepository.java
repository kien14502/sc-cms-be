package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.UserEntity;
import com.vnpt.mac.partner.entity.UserStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByPartnerIdAndStatusIn(UUID partnerId, Collection<UserStatus> statuses);
    Page<UserEntity> findByPartnerId(UUID partnerId, Pageable pageable);
    Page<UserEntity> findByPartnerIdIsNull(Pageable pageable);
}
