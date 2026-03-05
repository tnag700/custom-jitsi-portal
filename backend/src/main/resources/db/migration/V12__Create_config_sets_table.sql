CREATE TABLE config_sets (
    config_set_id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    environment_type VARCHAR(10) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    audience VARCHAR(255) NOT NULL,
    algorithm VARCHAR(10) NOT NULL,
    role_claim VARCHAR(255) NOT NULL DEFAULT 'role',
    signing_secret_encrypted TEXT,
    jwks_uri VARCHAR(1024),
    access_ttl_minutes INTEGER NOT NULL,
    refresh_ttl_minutes INTEGER,
    meetings_service_url VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_config_sets_tenant_id ON config_sets(tenant_id);
CREATE UNIQUE INDEX uq_config_sets_name_tenant_deleted ON config_sets(name, tenant_id, deleted);
CREATE UNIQUE INDEX uq_config_sets_env_tenant_status_deleted ON config_sets(environment_type, tenant_id, status, deleted);