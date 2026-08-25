CREATE TABLE registry_entries (
    id UUID PRIMARY KEY,
    entry_type VARCHAR(30) NOT NULL,
    namespace_key VARCHAR(255) NOT NULL,
    owner_app_id UUID NOT NULL REFERENCES applications(id),
    owner_version_id UUID REFERENCES app_versions(id),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_registry_entries_active_namespace
    ON registry_entries(entry_type, namespace_key)
    WHERE status IN ('RESERVED', 'ACTIVE');

CREATE TABLE voice_purposes (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    purpose_key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_action VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE INDEX idx_voice_purposes_version ON voice_purposes(version_id);

CREATE TABLE purpose_slots (
    id UUID PRIMARY KEY,
    purpose_id UUID NOT NULL REFERENCES voice_purposes(id),
    slot_key VARCHAR(100) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_purpose_slots_key ON purpose_slots(purpose_id, slot_key);

CREATE TABLE purpose_utterances (
    id UUID PRIMARY KEY,
    purpose_id UUID NOT NULL REFERENCES voice_purposes(id),
    phrase TEXT NOT NULL,
    slots JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_purpose_utterances_purpose ON purpose_utterances(purpose_id);
