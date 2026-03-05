CREATE TABLE meeting_invites (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    meeting_id VARCHAR(36) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    max_uses INTEGER NOT NULL DEFAULT 1,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    CONSTRAINT fk_invites_meeting_id FOREIGN KEY (meeting_id) REFERENCES meetings(meeting_id),
    CONSTRAINT chk_invite_role_valid CHECK (role IN ('host', 'moderator', 'participant')),
    CONSTRAINT chk_max_uses_positive CHECK (max_uses > 0),
    CONSTRAINT chk_used_count_nonnegative CHECK (used_count >= 0)
);

CREATE INDEX idx_invites_meeting_id ON meeting_invites(meeting_id);
CREATE INDEX idx_invites_token ON meeting_invites(token);
CREATE INDEX idx_invites_expires_at ON meeting_invites(expires_at);
