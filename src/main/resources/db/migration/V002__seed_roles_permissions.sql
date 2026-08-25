INSERT INTO permissions(id, code, description) VALUES
('00000000-0000-0000-0000-000000000101', 'partner.read.all', 'Read every partner'),
('00000000-0000-0000-0000-000000000102', 'partner.read', 'Read scoped partner'),
('00000000-0000-0000-0000-000000000103', 'partner.create', 'Create partner'),
('00000000-0000-0000-0000-000000000104', 'partner.update', 'Update partner'),
('00000000-0000-0000-0000-000000000105', 'partner.approve', 'Approve or reject partner'),
('00000000-0000-0000-0000-000000000106', 'partner.suspend', 'Suspend partner'),
('00000000-0000-0000-0000-000000000107', 'quota.read', 'Read quota'),
('00000000-0000-0000-0000-000000000108', 'quota.update', 'Update quota'),
('00000000-0000-0000-0000-000000000109', 'user.read', 'Read users'),
('00000000-0000-0000-0000-000000000110', 'user.invite', 'Invite developer'),
('00000000-0000-0000-0000-000000000111', 'app.assign', 'Assign developer to app'),
('00000000-0000-0000-0000-000000000112', 'admin.manage', 'Manage admin accounts'),
('00000000-0000-0000-0000-000000000113', 'token.manage.own', 'Manage own API tokens');

INSERT INTO roles(id, code, name) VALUES
('00000000-0000-0000-0000-000000000201', 'PLATFORM_ADMIN', 'Platform Admin'),
('00000000-0000-0000-0000-000000000202', 'ADMIN', 'Admin'),
('00000000-0000-0000-0000-000000000203', 'PARTNER_ADMIN', 'Partner Admin'),
('00000000-0000-0000-0000-000000000204', 'PARTNER_DEVELOPER', 'Partner Developer'),
('00000000-0000-0000-0000-000000000205', 'REVIEWER', 'Reviewer');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000201', id FROM permissions;

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000202', id FROM permissions
WHERE code IN ('partner.read.all', 'partner.read', 'user.read', 'quota.read');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions
WHERE code IN ('partner.read', 'quota.read', 'user.read', 'user.invite', 'app.assign', 'token.manage.own');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'PARTNER_DEVELOPER' AND permission.code IN ('partner.read', 'token.manage.own');
