package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AppVersionPermissionEntityTest {
    private AppVersionPermissionEntity pendingReview() {
        return AppVersionPermissionEntity.request(UUID.randomUUID(), UUID.randomUUID(), "Cần camera để quét mã QR",
                PermissionSensitivity.DANGEROUS, PermissionRequestStatus.PENDING_REVIEW, false);
    }

    @Test void requestedPermissionCanBeApproved() {
        var p = pendingReview();
        var reviewer = UUID.randomUUID();
        p.approve(reviewer);
        assertThat(p.getStatus()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(p.getDecidedBy()).isEqualTo(reviewer);
        assertThat(p.getDecidedAt()).isNotNull();
    }

    @Test void requestedPermissionCanBeRejectedWithReason() {
        var p = pendingReview();
        var reviewer = UUID.randomUUID();
        p.reject(reviewer, "Không phù hợp với mục đích ứng dụng");
        assertThat(p.getStatus()).isEqualTo(PermissionRequestStatus.REJECTED);
        assertThat(p.getDecisionReason()).isEqualTo("Không phù hợp với mục đích ứng dụng");
    }

    @Test void autoApprovedPermissionCannotBeApprovedAgain() {
        var p = AppVersionPermissionEntity.request(UUID.randomUUID(), UUID.randomUUID(), "Lưu file tải xuống",
                PermissionSensitivity.NORMAL, PermissionRequestStatus.AUTO_APPROVED, false);
        var ex = catchThrowableOfType(() -> p.approve(UUID.randomUUID()), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_NOT_PENDING_REVIEW);
    }

    @Test void alreadyDecidedPermissionCannotBeRejectedAgain() {
        var p = pendingReview();
        p.approve(UUID.randomUUID());
        var ex = catchThrowableOfType(() -> p.reject(UUID.randomUUID(), "lý do"), BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.PERMISSION_NOT_PENDING_REVIEW);
    }
}
