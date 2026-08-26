INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000201', id FROM permissions
ON CONFLICT DO NOTHING;
