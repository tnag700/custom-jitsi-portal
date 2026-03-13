package com.acme.jitsi.domains.meetings.usecase;

import java.time.Instant;

public record CreateMeetingCommand(
    String roomId,
    String title,
    String description,
    String meetingType,
    Instant startsAt,
    Instant endsAt,
    boolean allowGuests,
    boolean recordingEnabled,
    String actorId,
    String traceId) {
}
