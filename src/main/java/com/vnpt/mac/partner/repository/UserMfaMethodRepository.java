package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.MfaMethodType;
import com.vnpt.mac.partner.entity.UserMfaMethodEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaMethodRepository extends JpaRepository<UserMfaMethodEntity, UUID> {
    Optional<UserMfaMethodEntity> findFirstByUserIdAndMethodTypeOrderByVerifiedAtDesc(UUID userId, MfaMethodType type);
}
