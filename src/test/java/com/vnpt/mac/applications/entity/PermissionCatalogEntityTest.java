package com.vnpt.mac.applications.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogEntityTest {
    @Test void createStartsActiveWithTheGivenFields() {
        var p = PermissionCatalogEntity.create("BLUETOOTH", "Bluetooth", PermissionSensitivity.NORMAL, false);
        assertThat(p.getCode()).isEqualTo("BLUETOOTH");
        assertThat(p.getDisplayName()).isEqualTo("Bluetooth");
        assertThat(p.getSensitivity()).isEqualTo(PermissionSensitivity.NORMAL);
        assertThat(p.isRequiresManualReview()).isFalse();
        assertThat(p.isActive()).isTrue();
    }

    @Test void updateChangesDisplayNameSensitivityReviewFlagAndActiveState() {
        var p = PermissionCatalogEntity.create("BLUETOOTH", "Bluetooth", PermissionSensitivity.NORMAL, false);
        p.update("Bluetooth (BLE)", PermissionSensitivity.DANGEROUS, true, false);
        assertThat(p.getDisplayName()).isEqualTo("Bluetooth (BLE)");
        assertThat(p.getSensitivity()).isEqualTo(PermissionSensitivity.DANGEROUS);
        assertThat(p.isRequiresManualReview()).isTrue();
        assertThat(p.isActive()).isFalse();
    }
}
