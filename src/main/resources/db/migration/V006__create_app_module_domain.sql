CREATE TABLE version_artifacts (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    kind VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    signature_fingerprint VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_version_artifacts_version ON version_artifacts(version_id);

CREATE TABLE version_webapp_config (
    version_id UUID PRIMARY KEY REFERENCES app_versions(id),
    destination_url VARCHAR(500) NOT NULL,
    ssl_valid BOOLEAN NOT NULL DEFAULT FALSE,
    last_health_status INTEGER,
    last_checked_at TIMESTAMPTZ
);

CREATE TABLE version_module_config (
    version_id UUID PRIMARY KEY REFERENCES app_versions(id),
    module_namespace VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE validation_runs (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    status VARCHAR(20) NOT NULL,
    triggered_by UUID,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_validation_runs_version ON validation_runs(version_id);

CREATE TABLE validation_findings (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES validation_runs(id),
    rule_code VARCHAR(100) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    context JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_validation_findings_run ON validation_findings(run_id);

CREATE TABLE review_submissions (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    review_round INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_review_submissions_version ON review_submissions(version_id);

CREATE TABLE review_decisions (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES review_submissions(id),
    decision VARCHAR(20) NOT NULL,
    feedback TEXT,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_review_decisions_submission ON review_decisions(submission_id);

INSERT INTO app_categories(id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000301', 'UTILITIES', 'Utilities'),
    ('00000000-0000-0000-0000-000000000302', 'ENTERTAINMENT', 'Entertainment'),
    ('00000000-0000-0000-0000-000000000303', 'EDUCATION', 'Education'),
    ('00000000-0000-0000-0000-000000000304', 'PRODUCTIVITY', 'Productivity'),
    ('00000000-0000-0000-0000-000000000305', 'LIFESTYLE', 'Lifestyle');
