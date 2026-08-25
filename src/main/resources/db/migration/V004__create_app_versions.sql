CREATE TABLE app_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE app_versions (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL REFERENCES applications(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    version_code INTEGER NOT NULL,
    version_name VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    description_short VARCHAR(500),
    description_long TEXT,
    supported_languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    review_round INTEGER NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE UNIQUE INDEX uq_app_versions_app_version_code ON app_versions(app_id, version_code);
CREATE INDEX idx_app_versions_app ON app_versions(app_id);
CREATE INDEX idx_app_versions_partner ON app_versions(partner_id);

CREATE TABLE app_version_categories (
    version_id UUID NOT NULL REFERENCES app_versions(id),
    category_id UUID NOT NULL REFERENCES app_categories(id),
    PRIMARY KEY (version_id, category_id)
);

CREATE TABLE app_version_assets (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    asset_kind VARCHAR(30) NOT NULL,
    storage_url VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_app_version_assets_version ON app_version_assets(version_id);
