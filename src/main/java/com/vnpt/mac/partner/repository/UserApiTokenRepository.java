package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.UserApiTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserApiTokenRepository extends JpaRepository<UserApiTokenEntity, UUID> {
    List<UserApiTokenEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<UserApiTokenEntity> findByTokenPrefixAndRevokedAtIsNull(String prefix);
    Optional<UserApiTokenEntity> findByIdAndUserId(UUID id, UUID userId);
}
