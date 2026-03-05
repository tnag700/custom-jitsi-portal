ALTER TABLE meeting_invites DROP CONSTRAINT IF EXISTS chk_invite_role_valid;

ALTER TABLE meeting_invites
    ADD CONSTRAINT chk_invite_role_valid
    CHECK (role IN ('moderator', 'participant'));
