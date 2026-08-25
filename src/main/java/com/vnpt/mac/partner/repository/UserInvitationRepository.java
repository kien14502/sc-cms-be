package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.UserInvitationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitationEntity, UUID> {
    Optional<UserInvitationEntity> findByTokenHash(String tokenHash);
}
