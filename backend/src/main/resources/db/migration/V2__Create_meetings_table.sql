CREATE TABLE meetings (
    meeting_id VARCHAR(36) NOT NULL PRIMARY KEY,
    room_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    meeting_type VARCHAR(64) NOT NULL,
    config_set_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    allow_guests BOOLEAN NOT NULL,
    recording_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_meetings_room_id FOREIGN KEY (room_id) REFERENCES rooms(room_id)
);

CREATE INDEX idx_meetings_room_id ON meetings(room_id);
CREATE INDEX idx_meetings_room_id_created_at ON meetings(room_id, created_at DESC);
CREATE INDEX idx_meetings_status ON meetings(status);