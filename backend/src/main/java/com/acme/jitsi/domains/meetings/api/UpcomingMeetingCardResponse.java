package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.JoinAvailability;
import java.time.Instant;

record UpcomingMeetingCardResponse(
    String meetingId,
    String title,
    Instant startsAt,
    String roomName,
    JoinAvailability joinAvailability) {
}
