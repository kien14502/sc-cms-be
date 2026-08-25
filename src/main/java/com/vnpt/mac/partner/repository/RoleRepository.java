package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.RoleCode;
import com.vnpt.mac.partner.entity.RoleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByCode(RoleCode code);
}
