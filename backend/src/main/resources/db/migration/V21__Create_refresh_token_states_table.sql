CREATE TABLE refresh_token_states (
    token_id VARCHAR(255) PRIMARY KEY,
    subject_id VARCHAR(255) NOT NULL,
    meeting_id VARCHAR(255) NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idle_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_refresh_token_states_status CHECK (status IN ('ACTIVE', 'USED', 'REVOKED'))
);

CREATE INDEX idx_refresh_token_states_absolute_expiry
    ON refresh_token_states(absolute_expires_at);

CREATE TABLE refresh_token_store_metadata (
    singleton_id SMALLINT PRIMARY KEY,
    accept_issued_after TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_refresh_token_store_metadata_singleton CHECK (singleton_id = 1)
);

INSERT INTO refresh_token_store_metadata (singleton_id, accept_issued_after)
VALUES (1, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00');
