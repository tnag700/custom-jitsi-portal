CREATE TABLE meeting_participant_assignments (
    assignment_id VARCHAR(36) NOT NULL PRIMARY KEY,
    meeting_id VARCHAR(36) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_assignments_meeting_id FOREIGN KEY (meeting_id) REFERENCES meetings(meeting_id),
    CONSTRAINT uk_meeting_subject UNIQUE (meeting_id, subject_id),
    CONSTRAINT chk_role_valid CHECK (role IN ('host', 'moderator', 'participant'))
);

CREATE INDEX idx_assignments_meeting_id ON meeting_participant_assignments(meeting_id);
CREATE INDEX idx_assignments_subject_id ON meeting_participant_assignments(subject_id);
