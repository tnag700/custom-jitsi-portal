ALTER TABLE meeting_invites
    ADD COLUMN recipient_email VARCHAR(255);

ALTER TABLE meeting_invites
    ADD COLUMN recipient_user_id VARCHAR(100);

CREATE INDEX idx_invites_meeting_recipient_email
    ON meeting_invites (meeting_id, recipient_email);

CREATE INDEX idx_invites_meeting_recipient_user
    ON meeting_invites (meeting_id, recipient_user_id);
