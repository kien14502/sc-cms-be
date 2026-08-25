package com.vnpt.mac.partner.entity;

import com.vnpt.mac.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PartnerEntityTest {
    @Test void pendingPartnerCanBeApproved() {
        var partner=PartnerEntity.create("PTN-1","VNPT","0101","a@vnpt.vn",null,false);
        partner.approve();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }
    @Test void suspendedPartnerCanBeUnsuspended() {
        var partner=PartnerEntity.create("PTN-1","VNPT","0101","a@vnpt.vn",null,true);
        partner.suspend("Policy violation"); partner.unsuspend();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }
    @Test void activePartnerCannotBeApprovedAgain() {
        var partner=PartnerEntity.create("PTN-1","VNPT","0101","a@vnpt.vn",null,true);
        assertThatThrownBy(partner::approve).isInstanceOf(BusinessException.class);
    }
}
