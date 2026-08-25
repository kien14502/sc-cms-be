CREATE TABLE applications (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),
    app_code VARCHAR(50) NOT NULL UNIQUE,
    app_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    is_first_party BOOLEAN NOT NULL DEFAULT FALSE,
    kill_switch_active BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE INDEX idx_applications_partner ON applications(partner_id);

ALTER TABLE app_developer_assignments
    ADD CONSTRAINT fk_app_developer_assignments_app
    FOREIGN KEY (app_id) REFERENCES applications(id);
