package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AppVersionCapabilityEntityTest {
    private AppVersionCapabilityEntity pendingReview() {
        return AppVersionCapabilityEntity.request(UUID.randomUUID(), UUID.randomUUID(), CapabilityRequestStatus.PENDING_REVIEW);
    }

    @Test void requestedCapabilityCanBeApproved() {
        var c = pendingReview();
        var reviewer = UUID.randomUUID();
        c.approve(reviewer);
        assertThat(c.getStatus()).isEqualTo(CapabilityRequestStatus.APPROVED);
        assertThat(c.getDecidedBy()).isEqualTo(reviewer);
        assertThat(c.getDecidedAt()).isNotNull();
    }

    @Test void requestedCapabilityCanBeRejectedWithReason() {
        var c = pendingReview();
        c.reject(UUID.randomUUID(), "Không phù hợp với app type");
        assertThat(c.getStatus()).isEqualTo(CapabilityRequestStatus.REJECTED);
        assertThat(c.getDecisionReason()).isEqualTo("Không phù hợp với app type");
    }

    @Test void blockedCapabilityCannotBeApproved() {
        var c = AppVersionCapabilityEntity.request(UUID.randomUUID(), UUID.randomUUID(), CapabilityRequestStatus.BLOCKED);
        var ex = catchThrowableOfType(() -> c.approve(UUID.randomUUID()), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_NOT_PENDING_REVIEW);
    }

    @Test void alreadyDecidedCapabilityCannotBeRejectedAgain() {
        var c = pendingReview();
        c.approve(UUID.randomUUID());
        var ex = catchThrowableOfType(() -> c.reject(UUID.randomUUID(), "lý do"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_NOT_PENDING_REVIEW);
    }
}
