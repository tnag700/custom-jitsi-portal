CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    completion_date TIMESTAMP WITH TIME ZONE NULL,
    event_type VARCHAR(512) NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    serialized_event TEXT NOT NULL,
    status VARCHAR(32) NULL,
    completion_attempts INTEGER NOT NULL,
    last_resubmission_date TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_event_publication_completion_publication_date
    ON event_publication (completion_date, publication_date);

CREATE INDEX idx_event_publication_status_publication_date
    ON event_publication (status, publication_date);

CREATE INDEX idx_event_publication_listener_status
    ON event_publication (listener_id, status);