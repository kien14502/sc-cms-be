INSERT INTO permissions(id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000114', 'app.read.all', 'Read every application'),
    ('00000000-0000-0000-0000-000000000115', 'app.read', 'Read scoped application'),
    ('00000000-0000-0000-0000-000000000116', 'app.create', 'Create application'),
    ('00000000-0000-0000-0000-000000000117', 'version.read', 'Read version'),
    ('00000000-0000-0000-0000-000000000118', 'version.create', 'Create version'),
    ('00000000-0000-0000-0000-000000000119', 'version.update', 'Update version metadata'),
    ('00000000-0000-0000-0000-000000000120', 'artifact.upload', 'Upload version artifact/config'),
    ('00000000-0000-0000-0000-000000000121', 'version.submit', 'Submit version for review'),
    ('00000000-0000-0000-0000-000000000122', 'version.review', 'Approve/Reject/Request changes on a version');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000201', id FROM permissions
WHERE code IN ('app.read.all', 'app.read', 'app.create', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit', 'version.review');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions
WHERE code IN ('app.read', 'app.create', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000204', id FROM permissions
WHERE code IN ('app.read', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000205', id FROM permissions
WHERE code IN ('app.read', 'version.read', 'version.review');
