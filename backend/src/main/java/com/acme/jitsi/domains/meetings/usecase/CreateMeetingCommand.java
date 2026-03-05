package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.rooms.service.Room;
import java.time.Instant;

public record CreateMeetingCommand(
    Room room,
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
