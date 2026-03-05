package com.acme.jitsi.domains.meetings.api;

import java.time.Instant;

record MeetingResponse(
    String meetingId,
    String roomId,
    String title,
    String description,
    String meetingType,
    String configSetId,
    String status,
    Instant startsAt,
    Instant endsAt,
    boolean allowGuests,
    boolean recordingEnabled,
    Instant createdAt,
    Instant updatedAt) {
}