package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AppVersionEntityTest {
    private AppVersionEntity draft() {
        return AppVersionEntity.create(UUID.randomUUID(), UUID.randomUUID(), 1, "1.0.0",
                "My MiniApp", "com.vnpt.miniapp", "short", "long", List.of("vi", "en"));
    }

    @Test void draftVersionCanBeSubmitted() {
        var v = draft();
        v.submit();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(v.getReviewRound()).isEqualTo(1);
    }

    @Test void inReviewVersionCanBeApproved() {
        var v = draft();
        v.submit();
        v.approve();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.APPROVED);
    }

    @Test void inReviewVersionCanBeSentBackForChangesThenResubmitted() {
        var v = draft();
        v.submit();
        v.requestChanges();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.CHANGES_REQUESTED);
        v.submit();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(v.getReviewRound()).isEqualTo(2);
    }

    @Test void approvedVersionCannotBeEdited() {
        var v = draft();
        v.submit();
        v.approve();
        assertThatThrownBy(v::assertEditable).isInstanceOf(BusinessException.class);
    }

    @Test void draftVersionCannotBeApprovedDirectly() {
        assertThatThrownBy(draft()::approve).isInstanceOf(BusinessException.class);
    }

    @Test void rejectedVersionIsTerminal() {
        var v = draft();
        v.submit();
        v.reject();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.REJECTED);
        assertThatThrownBy(v::submit).isInstanceOf(BusinessException.class);
    }
}
