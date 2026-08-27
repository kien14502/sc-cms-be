package com.vnpt.mac.applications.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCatalogEntityTest {
    @Test void createStartsActiveWithTheGivenFields() {
        var c = CapabilityCatalogEntity.create("PUSH_NOTIFICATION", "Gửi thông báo đẩy",
                List.of(ApplicationType.MINIAPP, ApplicationType.WEBAPP));
        assertThat(c.getCode()).isEqualTo("PUSH_NOTIFICATION");
        assertThat(c.getDisplayName()).isEqualTo("Gửi thông báo đẩy");
        assertThat(c.getAllowedAppTypes()).containsExactly(ApplicationType.MINIAPP, ApplicationType.WEBAPP);
        assertThat(c.isActive()).isTrue();
    }

    @Test void allowsAppTypeIsTrueOnlyForTypesInTheAllowedList() {
        var c = CapabilityCatalogEntity.create("DEEP_LINK", "Deep link", List.of(ApplicationType.MINIAPP, ApplicationType.APP2APP));
        assertThat(c.allowsAppType(ApplicationType.MINIAPP)).isTrue();
        assertThat(c.allowsAppType(ApplicationType.WEBAPP)).isFalse();
    }

    @Test void updateChangesDisplayNameAllowedAppTypesAndActiveState() {
        var c = CapabilityCatalogEntity.create("DEEP_LINK", "Deep link", List.of(ApplicationType.MINIAPP));
        c.update("Deep Link", List.of(ApplicationType.MINIAPP, ApplicationType.FEATURE_APP), false);
        assertThat(c.getDisplayName()).isEqualTo("Deep Link");
        assertThat(c.getAllowedAppTypes()).containsExactly(ApplicationType.MINIAPP, ApplicationType.FEATURE_APP);
        assertThat(c.isActive()).isFalse();
    }
}
