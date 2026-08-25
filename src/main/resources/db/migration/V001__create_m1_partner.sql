CREATE TABLE partners (
    id UUID PRIMARY KEY,
    partner_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(50) UNIQUE,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    suspend_reason TEXT,
    suspended_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE TABLE partner_status_history (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT,
    changed_by UUID,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE partner_quotas (
    partner_id UUID PRIMARY KEY REFERENCES partners(id),
    max_apps INTEGER NOT NULL CHECK (max_apps >= 0),
    max_developers INTEGER NOT NULL CHECK (max_developers >= 0),
    max_concurrent_submissions INTEGER NOT NULL CHECK (max_concurrent_submissions >= 0),
    max_storage_bytes BIGINT NOT NULL CHECK (max_storage_bytes >= 0),
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    partner_id UUID REFERENCES partners(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    full_name VARCHAR(255) NOT NULL,
    public_email VARCHAR(255),
    bio TEXT,
    status VARCHAR(30) NOT NULL,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_mfa_methods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    method_type VARCHAR(30) NOT NULL,
    secret_value VARCHAR(255) NOT NULL,
    verified_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ
);

CREATE TABLE user_api_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    token_prefix VARCHAR(32) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    scopes TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_developer_assignments (
    id UUID PRIMARY KEY,
    app_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_active_app_developer
    ON app_developer_assignments(app_id, user_id) WHERE revoked_at IS NULL;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    actor_email VARCHAR(255),
    actor_roles TEXT,
    partner_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    ip_address VARCHAR(64),
    user_agent TEXT,
    before_state TEXT,
    after_state TEXT,
    correlation_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_partners_status ON partners(status);
CREATE INDEX idx_users_partner ON users(partner_id);
CREATE INDEX idx_audit_partner_created ON audit_logs(partner_id, created_at DESC);
CREATE INDEX idx_api_token_prefix ON user_api_tokens(token_prefix);
