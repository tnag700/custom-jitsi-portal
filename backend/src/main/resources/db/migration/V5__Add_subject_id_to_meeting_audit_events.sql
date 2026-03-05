ALTER TABLE meeting_audit_events
    ADD COLUMN subject_id VARCHAR(255);

CREATE INDEX idx_meeting_audit_events_subject_id
    ON meeting_audit_events (subject_id);