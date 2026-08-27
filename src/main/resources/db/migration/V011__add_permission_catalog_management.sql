-- Closes the M3 "Platform Admin: Full" gap on the permission catalog module: V010 only
-- exposed a read endpoint for the catalog/rules, with no way to manage them at runtime.
-- PLATFORM_ADMIN already receives this via the R__grant_platform_admin_all_permissions.sql
-- repeatable migration's blanket SELECT * FROM permissions grant.
INSERT INTO permissions(id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000126', 'permission.catalog.manage', 'Create/update permission catalog entries and app-type rules');
