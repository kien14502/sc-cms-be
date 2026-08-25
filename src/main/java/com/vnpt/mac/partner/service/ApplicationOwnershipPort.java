package com.vnpt.mac.partner.service;

import java.util.UUID;

public interface ApplicationOwnershipPort {
    boolean belongsToPartner(UUID appId, UUID partnerId);
}
