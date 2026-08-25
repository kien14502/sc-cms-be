package com.vnpt.mac.partner.service;

import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.*;
import com.vnpt.mac.partner.dto.UserDtos.*;
import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;
import com.vnpt.mac.security.CurrentUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserInvitationRepository invitations;
    private final PartnerService partners;
    private final PartnerQuotaRepository quotas;
    private final TokenValueService tokens;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final AuditService audit;
    private final long expiryHours;

    public UserService(UserRepository users, RoleRepository roles, UserInvitationRepository invitations, PartnerService partners,
                       PartnerQuotaRepository quotas, TokenValueService tokens, PasswordEncoder encoder, CurrentUser current,
                       AuditService audit, @Value("${mac.invitation.expiration-hours:24}") long expiryHours) {
        this.users = users;
        this.roles = roles;
        this.invitations = invitations;
        this.partners = partners;
        this.quotas = quotas;
        this.tokens = tokens;
        this.encoder = encoder;
        this.current = current;
        this.audit = audit;
        this.expiryHours = expiryHours;
    }

    @Transactional
    public InvitationResponse invite(UUID partnerId, InviteUserRequest request) {
        var partner = partners.require(partnerId);
        partner.requireActive();
        if (users.existsByEmailIgnoreCase(request.email())) throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
        if (request.role() != RoleCode.PARTNER_ADMIN && request.role() != RoleCode.PARTNER_DEVELOPER)
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Chỉ được mời role Partner");
        var quota = quotas.findById(partnerId).orElseThrow();
        long used = users.countByPartnerIdAndStatusIn(partnerId, List.of(UserStatus.INVITED, UserStatus.ACTIVE));
        if (used >= quota.getMaxDevelopers()) throw new BusinessException(ErrorCode.PARTNER_QUOTA_EXCEEDED);
        var role = roles.findByCode(request.role()).orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        var user = users.save(UserEntity.invited(partnerId, request.email(), request.fullName(), role));
        String raw = tokens.random(32);
        Instant expires = Instant.now().plus(expiryHours, ChronoUnit.HOURS);
        invitations.save(UserInvitationEntity.create(user.getId(), tokens.sha256(raw), expires));
        audit.log(partnerId, "USER_INVITED", "USER", user.getId(), null, UserResponse.from(user));
        return new InvitationResponse(user.getId(), raw, expires);
    }

    @Transactional
    public void accept(AcceptInvitationRequest request) {
        var invitation = invitations.findByTokenHash(tokens.sha256(request.token()))
                .filter(UserInvitationEntity::usable).orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_INVALID));
        var user = users.findById(invitation.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.activate(encoder.encode(request.password()));
        invitation.consume();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(UUID partnerId, Pageable pageable) {
        return users.findByPartnerId(partnerId, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listAdmins(Pageable pageable) {
        return users.findByPartnerIdIsNull(pageable).map(UserResponse::from);
    }

    @Transactional
    public InvitationResponse createAdmin(CreateAdminRequest request) {
        if (request.role() != RoleCode.PLATFORM_ADMIN && request.role() != RoleCode.ADMIN && request.role() != RoleCode.REVIEWER)
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "Role Admin không hợp lệ");
        if (users.existsByEmailIgnoreCase(request.email())) throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
        var role = roles.findByCode(request.role()).orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        var user = users.save(UserEntity.invited(null, request.email(), request.fullName(), role));
        String raw = tokens.random(32);
        Instant expires = Instant.now().plus(expiryHours, ChronoUnit.HOURS);
        invitations.save(UserInvitationEntity.create(user.getId(), tokens.sha256(raw), expires));
        audit.log(null, "ADMIN_INVITED", "USER", user.getId(), null, UserResponse.from(user));
        return new InvitationResponse(user.getId(), raw, expires);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(requireCurrent());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest r) {
        var u = requireCurrent();
        u.updateProfile(r.fullName(), r.publicEmail(), r.bio());
        return UserResponse.from(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest r) {
        var u = requireCurrent();
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash()))
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        u.changePassword(encoder.encode(r.newPassword()));
    }

    public UserEntity requireCurrent() {
        return users.findById(current.id()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
