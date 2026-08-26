INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000202', id FROM permissions
WHERE code IN ('app.read.all', 'app.read', 'version.read');

CREATE UNIQUE INDEX uq_review_decisions_submission ON review_decisions(submission_id);
