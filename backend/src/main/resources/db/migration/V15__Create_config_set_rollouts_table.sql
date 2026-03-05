CREATE TABLE config_set_rollouts (
    rollout_id VARCHAR(36) NOT NULL PRIMARY KEY,
    config_set_id VARCHAR(36) NOT NULL,
    previous_config_set_id VARCHAR(36),
    tenant_id VARCHAR(255) NOT NULL,
    environment_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    validation_errors TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    actor_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255)
);

CREATE INDEX idx_rollouts_tenant_env ON config_set_rollouts(tenant_id, environment_type);
CREATE INDEX idx_rollouts_config_set ON config_set_rollouts(config_set_id);
CREATE INDEX idx_rollouts_started_at ON config_set_rollouts(started_at);