CREATE TABLE meeting_audit_events (
  id BIGSERIAL PRIMARY KEY,
  action_type VARCHAR(32) NOT NULL,
  room_id VARCHAR(64) NOT NULL,
  meeting_id VARCHAR(64) NOT NULL,
  actor_id VARCHAR(255) NOT NULL,
  trace_id VARCHAR(128),
  changed_fields TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_meeting_audit_events_meeting_id ON meeting_audit_events (meeting_id);
CREATE INDEX idx_meeting_audit_events_room_id ON meeting_audit_events (room_id);
CREATE INDEX idx_meeting_audit_events_created_at ON meeting_audit_events (created_at DESC);
