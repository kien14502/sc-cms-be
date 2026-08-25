package com.vnpt.mac.partner.service;

import com.vnpt.mac.applications.repository.ApplicationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ApplicationOwnershipAdapter implements ApplicationOwnershipPort {
    private final ApplicationRepository applications;

    public ApplicationOwnershipAdapter(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    public boolean belongsToPartner(UUID appId, UUID partnerId) {
        return applications.existsByIdAndPartnerId(appId, partnerId);
    }
}
