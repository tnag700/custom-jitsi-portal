CREATE TABLE auth_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(255),
    subject_id VARCHAR(255),
    meeting_id VARCHAR(64),
    token_id VARCHAR(255),
    error_code VARCHAR(64),
    trace_id VARCHAR(128),
    tenant_id VARCHAR(255),
    client_context TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_audit_event_type ON auth_audit_events(event_type);
CREATE INDEX idx_auth_audit_subject_id ON auth_audit_events(subject_id);
CREATE INDEX idx_auth_audit_meeting_id ON auth_audit_events(meeting_id);
CREATE INDEX idx_auth_audit_trace_id ON auth_audit_events(trace_id);
CREATE INDEX idx_auth_audit_occurred_at ON auth_audit_events(occurred_at DESC);