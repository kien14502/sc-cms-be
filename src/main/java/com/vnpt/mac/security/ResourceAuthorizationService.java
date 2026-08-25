package com.vnpt.mac.security;

import com.vnpt.mac.partner.repository.AppDeveloperAssignmentRepository;
import com.vnpt.mac.partner.service.ApplicationOwnershipPort;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component("resourceAuth")
public class ResourceAuthorizationService {
    private final CurrentUser current;
    private final AppDeveloperAssignmentRepository assignments;
    private final ApplicationOwnershipPort ownership;

    public ResourceAuthorizationService(CurrentUser current, AppDeveloperAssignmentRepository assignments, ApplicationOwnershipPort ownership) {
        this.current = current;
        this.assignments = assignments;
        this.ownership = ownership;
    }

    public boolean partner(UUID partnerId) {
        var p = current.require();
        return hasRole(p, "PLATFORM_ADMIN") || hasRole(p, "ADMIN") || (p.partnerId() != null && p.partnerId().equals(partnerId));
    }

    public boolean app(UUID appId) {
        var p = current.require();
        return hasRole(p, "PLATFORM_ADMIN") || hasRole(p, "ADMIN")
                || (hasRole(p, "PARTNER_ADMIN") && p.partnerId() != null && ownership.belongsToPartner(appId, p.partnerId()))
                || assignments.findByAppIdAndUserIdAndRevokedAtIsNull(appId, p.userId()).isPresent();
    }

    private boolean hasRole(MacPrincipal p, String role) {
        return p.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
