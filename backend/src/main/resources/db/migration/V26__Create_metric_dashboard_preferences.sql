CREATE TABLE metric_dashboard_preferences (
    tenant_id TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    layout JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, subject_id)
);
