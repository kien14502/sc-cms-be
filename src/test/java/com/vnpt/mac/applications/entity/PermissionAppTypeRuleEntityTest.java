package com.vnpt.mac.applications.entity;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionAppTypeRuleEntityTest {
    @Test void createStoresTheGivenFields() {
        var permissionId = UUID.randomUUID();
        var rule = PermissionAppTypeRuleEntity.create(permissionId, ApplicationType.WEBAPP, RuleEffect.DENY,
                "WebApp không được truy cập phần cứng native");
        assertThat(rule.getPermissionId()).isEqualTo(permissionId);
        assertThat(rule.getAppType()).isEqualTo(ApplicationType.WEBAPP);
        assertThat(rule.getEffect()).isEqualTo(RuleEffect.DENY);
        assertThat(rule.getReason()).isEqualTo("WebApp không được truy cập phần cứng native");
    }

    @Test void updateChangesEffectAndReason() {
        var rule = PermissionAppTypeRuleEntity.create(UUID.randomUUID(), ApplicationType.WEBAPP, RuleEffect.DENY, "cũ");
        rule.update(RuleEffect.CONDITIONAL, "mới");
        assertThat(rule.getEffect()).isEqualTo(RuleEffect.CONDITIONAL);
        assertThat(rule.getReason()).isEqualTo("mới");
    }
}
