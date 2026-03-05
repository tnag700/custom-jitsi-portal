CREATE TABLE user_profiles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    subject_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    full_name VARCHAR(500) NOT NULL,
    organization VARCHAR(500) NOT NULL,
    position VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_profiles_subject_id UNIQUE (subject_id)
);

CREATE INDEX idx_profiles_tenant_id ON user_profiles(tenant_id);
CREATE INDEX idx_profiles_organization ON user_profiles(tenant_id, organization);
CREATE INDEX idx_profiles_full_name ON user_profiles(tenant_id, full_name);
