package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.Meeting;
import java.time.Instant;

public record UpdateMeetingCommand(
    Meeting existing,
    String title,
    String description,
    String meetingType,
    Instant startsAt,
    Instant endsAt,
    Boolean allowGuests,
    Boolean recordingEnabled,
    String actorId,
    String traceId) {
}
