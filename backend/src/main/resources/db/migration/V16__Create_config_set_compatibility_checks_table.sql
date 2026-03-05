CREATE TABLE config_set_compatibility_checks (
    check_id VARCHAR(36) NOT NULL PRIMARY KEY,
    config_set_id VARCHAR(36) NOT NULL,
    compatible BOOLEAN NOT NULL,
    mismatch_codes TEXT,
    details TEXT,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trace_id VARCHAR(255)
);

CREATE INDEX idx_config_compat_checks_config_set ON config_set_compatibility_checks(config_set_id);
CREATE INDEX idx_config_compat_checks_checked_at ON config_set_compatibility_checks(checked_at);