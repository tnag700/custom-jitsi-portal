CREATE TABLE config_set_audit_events (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    config_set_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255),
    changed_fields TEXT,
    old_values TEXT,
    new_values TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_config_set_audit_config_set_id ON config_set_audit_events(config_set_id);
CREATE INDEX idx_config_set_audit_event_type ON config_set_audit_events(event_type);
CREATE INDEX idx_config_set_audit_occurred_at ON config_set_audit_events(occurred_at);