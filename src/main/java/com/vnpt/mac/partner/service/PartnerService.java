package com.vnpt.mac.partner.service;

import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.dto.PartnerDtos.*;
import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;
import com.vnpt.mac.security.CurrentUser;

import java.util.UUID;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerService {
    private final PartnerRepository partners;
    private final PartnerQuotaRepository quotas;
    private final PartnerStatusHistoryRepository history;
    private final UserRepository users;
    private final ApplicationCountPort appCount;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public PartnerService(PartnerRepository partners, PartnerQuotaRepository quotas, PartnerStatusHistoryRepository history,
                          UserRepository users, ApplicationCountPort appCount, CurrentUser currentUser, AuditService audit) {
        this.partners = partners;
        this.quotas = quotas;
        this.history = history;
        this.users = users;
        this.appCount = appCount;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public PartnerResponse create(CreatePartnerRequest request) {
        if (request.taxCode() != null && !request.taxCode().isBlank() && partners.existsByTaxCodeIgnoreCase(request.taxCode()))
            throw new BusinessException(ErrorCode.PARTNER_TAX_CODE_EXISTS);
        String code = "PTN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var p = PartnerEntity.create(code, request.name(), request.taxCode(), request.contactEmail(), request.contactPhone(), request.activateImmediately());
        partners.save(p);
        quotas.save(PartnerQuotaEntity.defaults(p.getId()));
        history.save(PartnerStatusHistoryEntity.of(p.getId(), null, p.getStatus(), "Created", currentUser.id()));
        audit.log(p.getId(), "PARTNER_CREATED", "PARTNER", p.getId(), null, PartnerResponse.from(p));
        return PartnerResponse.from(p);
    }

    @Transactional(readOnly = true)
    public Page<PartnerResponse> search(PartnerStatus status, String q, Pageable pageable) {
        Page<PartnerEntity> result = status != null && q != null && !q.isBlank() ? partners.findByStatusAndNameContainingIgnoreCase(status, q, pageable)
                : status != null ? partners.findByStatus(status, pageable)
                : q != null && !q.isBlank() ? partners.findByNameContainingIgnoreCase(q, pageable) : partners.findAll(pageable);
        return result.map(PartnerResponse::from);
    }

    @Transactional(readOnly = true)
    public PartnerResponse get(UUID id) {
        return PartnerResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public java.util.List<StatusHistoryResponse> statusHistory(UUID id) {
        require(id);
        return history.findByPartnerIdOrderByChangedAtDesc(id).stream().map(StatusHistoryResponse::from).toList();
    }

    @Transactional
    public PartnerResponse update(UUID id, UpdatePartnerRequest request) {
        var p = require(id);
        var before = PartnerResponse.from(p);
        p.update(request.name(), request.contactEmail(), request.contactPhone());
        audit.log(id, "PARTNER_UPDATED", "PARTNER", id, before, PartnerResponse.from(p));
        return PartnerResponse.from(p);
    }

    @Transactional
    public PartnerResponse approve(UUID id) {
        return transition(id, "PARTNER_APPROVED", null, p -> p.approve());
    }

    @Transactional
    public PartnerResponse reject(UUID id, String reason) {
        return transition(id, "PARTNER_REJECTED", reason, p -> p.reject());
    }

    @Transactional
    public PartnerResponse suspend(UUID id, String reason) {
        return transition(id, "PARTNER_SUSPENDED", reason, p -> p.suspend(reason));
    }

    @Transactional
    public PartnerResponse unsuspend(UUID id) {
        return transition(id, "PARTNER_UNSUSPENDED", null, PartnerEntity::unsuspend);
    }

    private PartnerResponse transition(UUID id, String action, String reason, java.util.function.Consumer<PartnerEntity> change) {
        var p = require(id);
        var from = p.getStatus();
        var before = PartnerResponse.from(p);
        change.accept(p);
        history.save(PartnerStatusHistoryEntity.of(id, from, p.getStatus(), reason, currentUser.id()));
        audit.log(id, action, "PARTNER", id, before, PartnerResponse.from(p));
        return PartnerResponse.from(p);
    }

    @Transactional(readOnly = true)
    public QuotaResponse quota(UUID id) {
        require(id);
        var q = quotas.findById(id).orElseThrow();
        long developers = users.countByPartnerIdAndStatusIn(id, java.util.List.of(UserStatus.INVITED, UserStatus.ACTIVE));
        return QuotaResponse.from(q, developers, appCount.countApps(id));
    }

    @Transactional
    public QuotaResponse updateQuota(UUID id, UpdateQuotaRequest request) {
        require(id);
        var q = quotas.findById(id).orElseThrow();
        q.update(request.maxApps(), request.maxDevelopers(), request.maxConcurrentSubmissions(), request.maxStorageBytes());
        audit.log(id, "PARTNER_QUOTA_UPDATED", "PARTNER_QUOTA", id, null, request);
        return quota(id);
    }

    public PartnerEntity require(UUID id) {
        return partners.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTNER_NOT_FOUND));
    }
}
