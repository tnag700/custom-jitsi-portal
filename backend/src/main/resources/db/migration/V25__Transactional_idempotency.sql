CREATE TABLE idempotency_requests (
    request_key VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_idempotency_requests_expiry ON idempotency_requests(expires_at);
