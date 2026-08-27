CREATE TABLE permission_catalog (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    sensitivity VARCHAR(20) NOT NULL,
    requires_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE permission_app_type_rules (
    id UUID PRIMARY KEY,
    permission_id UUID NOT NULL REFERENCES permission_catalog(id),
    app_type VARCHAR(20) NOT NULL,
    effect VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    UNIQUE (permission_id, app_type)
);

CREATE TABLE app_version_permissions (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    permission_id UUID NOT NULL REFERENCES permission_catalog(id),
    justification TEXT NOT NULL,
    resolved_sensitivity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_escalation BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by UUID,
    decision_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    UNIQUE (version_id, permission_id),
    CHECK (length(trim(justification)) >= 20)
);

CREATE TABLE permission_events (
    id UUID PRIMARY KEY,
    app_version_permission_id UUID NOT NULL REFERENCES app_version_permissions(id),
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID,
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_app_version_permissions_version ON app_version_permissions(version_id);
CREATE INDEX idx_permission_events_permission ON permission_events(app_version_permission_id);

-- Seed catalog: common device permissions with sensitivity classification.
INSERT INTO permission_catalog(id, code, display_name, sensitivity, requires_manual_review, is_active) VALUES
    ('00000000-0000-0000-0000-000000000301', 'CAMERA', 'Camera', 'DANGEROUS', TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000302', 'MICROPHONE', 'Microphone', 'DANGEROUS', TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000303', 'LOCATION', 'Vị trí', 'DANGEROUS', TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000304', 'CONTACTS', 'Danh bạ', 'DANGEROUS', TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000305', 'NFC', 'NFC', 'DANGEROUS', TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000306', 'STORAGE', 'Bộ nhớ thiết bị', 'NORMAL', FALSE, TRUE),
    ('00000000-0000-0000-0000-000000000307', 'NOTIFICATIONS', 'Thông báo', 'NORMAL', FALSE, TRUE),
    ('00000000-0000-0000-0000-000000000308', 'BLUETOOTH', 'Bluetooth', 'NORMAL', FALSE, TRUE);

-- App type rules: App2App cannot request any device permission (protocol-only app type).
INSERT INTO permission_app_type_rules(id, permission_id, app_type, effect, reason)
SELECT gen_random_uuid(), id, 'APP2APP', 'DENY', 'App2App không có UI runtime, không được xin permission thiết bị'
FROM permission_catalog;

-- WebApp is blocked from native-hardware permissions (per design: "Giới hạn, chặn native hardware").
INSERT INTO permission_app_type_rules(id, permission_id, app_type, effect, reason)
SELECT gen_random_uuid(), id, 'WEBAPP', 'DENY', 'WebApp không được truy cập phần cứng native'
FROM permission_catalog WHERE code IN ('CAMERA', 'MICROPHONE', 'LOCATION', 'CONTACTS', 'NFC', 'BLUETOOTH');

-- New IAM permissions for the M3 module.
INSERT INTO permissions(id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000123', 'permission.catalog.read', 'Read permission catalog'),
    ('00000000-0000-0000-0000-000000000124', 'permission.request', 'Request a device permission on a version'),
    ('00000000-0000-0000-0000-000000000125', 'permission.decide', 'Approve/Reject a pending permission request');

-- ADMIN: read-only, mirrors its M1/M2 pattern.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000202', id FROM permissions
WHERE code = 'permission.catalog.read';

-- PARTNER_ADMIN: read-only visibility into the catalog.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions
WHERE code = 'permission.catalog.read';

-- PARTNER_DEVELOPER: reads the catalog and declares (requests) permissions.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000204', id FROM permissions
WHERE code IN ('permission.catalog.read', 'permission.request');

-- REVIEWER: reads the catalog and approves/rejects requests.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000205', id FROM permissions
WHERE code IN ('permission.catalog.read', 'permission.decide');
