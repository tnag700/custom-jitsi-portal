CREATE TABLE admin_incident_coordination_state (
    id BIGSERIAL PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    owner VARCHAR(255),
    workflow_status VARCHAR(64) NOT NULL,
    ticket_reference VARCHAR(255),
    ticket_status VARCHAR(64) NOT NULL,
    ticket_url VARCHAR(1000),
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_admin_incident_coordination_state UNIQUE (incident_id, tenant_id, environment)
);

CREATE TABLE admin_incident_coordination_audit_events (
    id BIGSERIAL PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128),
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_incident_coordination_state_lookup
    ON admin_incident_coordination_state(incident_id, tenant_id, environment);

CREATE INDEX idx_admin_incident_coordination_audit_lookup
    ON admin_incident_coordination_audit_events(incident_id, tenant_id, environment, created_at DESC);