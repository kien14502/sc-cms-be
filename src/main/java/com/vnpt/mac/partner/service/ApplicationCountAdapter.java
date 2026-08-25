package com.vnpt.mac.partner.service;

import com.vnpt.mac.partner.entity.ApplicationStatus;
import com.vnpt.mac.partner.repository.ApplicationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ApplicationCountAdapter implements ApplicationCountPort {
    private final ApplicationRepository applications;

    public ApplicationCountAdapter(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    public long countApps(UUID partnerId) {
        return applications.countByPartnerIdAndStatus(partnerId, ApplicationStatus.ACTIVE);
    }
}
