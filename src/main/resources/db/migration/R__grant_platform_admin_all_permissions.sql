-- Touched on V010 (M3 permission catalog) so Flyway re-applies this and
-- PLATFORM_ADMIN picks up the new permission.* codes on already-migrated DBs.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000201', id FROM permissions
ON CONFLICT DO NOTHING;
