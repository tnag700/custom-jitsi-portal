ALTER TABLE refresh_token_states ADD COLUMN family_id VARCHAR(255);

-- Historical parent links were not stored. Force SSO instead of guessing families.
UPDATE refresh_token_states SET family_id = token_id, status = 'REVOKED';
ALTER TABLE refresh_token_states ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_refresh_token_states_family ON refresh_token_states(family_id);

-- Reject even previously signed tokens which had never been registered in the store.
UPDATE refresh_token_store_metadata
SET accept_issued_after = GREATEST(accept_issued_after, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE singleton_id = 1;
