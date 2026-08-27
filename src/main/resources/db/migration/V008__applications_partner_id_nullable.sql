-- Applications/versions/review submissions created directly by a platform admin (no partnerId,
-- e.g. first-party apps) must be able to omit a partner. The FK is preserved; only the NOT NULL
-- constraint is relaxed.
ALTER TABLE applications ALTER COLUMN partner_id DROP NOT NULL;
ALTER TABLE app_versions ALTER COLUMN partner_id DROP NOT NULL;
ALTER TABLE review_submissions ALTER COLUMN partner_id DROP NOT NULL;
