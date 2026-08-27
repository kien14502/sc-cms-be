CREATE TABLE capability_catalog (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    allowed_app_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE app_version_capabilities (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    capability_id UUID NOT NULL REFERENCES capability_catalog(id),
    status VARCHAR(20) NOT NULL,
    decided_by UUID,
    decision_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    UNIQUE (version_id, capability_id)
);
CREATE INDEX idx_app_version_capabilities_version ON app_version_capabilities(version_id);

-- Seed catalog: a few representative device/runtime capabilities with their app-type scope.
INSERT INTO capability_catalog(id, code, display_name, allowed_app_types, is_active) VALUES
    ('00000000-0000-0000-0000-000000000401', 'BACKGROUND_LOCATION', 'Chạy nền lấy vị trí', '["MINIAPP","FEATURE_APP"]'::jsonb, TRUE),
    ('00000000-0000-0000-0000-000000000402', 'PUSH_NOTIFICATION', 'Gửi thông báo đẩy', '["MINIAPP","WEBAPP","FEATURE_APP","APP_MODULE"]'::jsonb, TRUE),
    ('00000000-0000-0000-0000-000000000403', 'DEEP_LINK', 'Mở app qua deep link', '["MINIAPP","APP2APP","FEATURE_APP"]'::jsonb, TRUE),
    ('00000000-0000-0000-0000-000000000404', 'BIOMETRIC_AUTH', 'Xác thực sinh trắc học', '["MINIAPP","FEATURE_APP"]'::jsonb, TRUE);

-- New IAM permissions for the M4 module.
INSERT INTO permissions(id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000127', 'capability.catalog.read', 'Read capability catalog'),
    ('00000000-0000-0000-0000-000000000128', 'capability.catalog.manage', 'Manage capability catalog'),
    ('00000000-0000-0000-0000-000000000129', 'capability.request', 'Declare a capability on a version'),
    ('00000000-0000-0000-0000-000000000130', 'capability.decide', 'Approve/Reject a pending capability request');

-- ADMIN: read-only, mirrors its M1/M2/M3 pattern.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000202', id FROM permissions
WHERE code = 'capability.catalog.read';

-- PARTNER_ADMIN: read-only visibility into the catalog.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions
WHERE code = 'capability.catalog.read';

-- PARTNER_DEVELOPER: reads the catalog and declares (requests) capabilities.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000204', id FROM permissions
WHERE code IN ('capability.catalog.read', 'capability.request');

-- REVIEWER: reads the catalog and approves/rejects requests.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000205', id FROM permissions
WHERE code IN ('capability.catalog.read', 'capability.decide');
