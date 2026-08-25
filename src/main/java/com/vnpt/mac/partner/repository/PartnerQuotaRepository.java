package com.vnpt.mac.partner.repository;

import com.vnpt.mac.partner.entity.PartnerQuotaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerQuotaRepository extends JpaRepository<PartnerQuotaEntity, UUID> {}
