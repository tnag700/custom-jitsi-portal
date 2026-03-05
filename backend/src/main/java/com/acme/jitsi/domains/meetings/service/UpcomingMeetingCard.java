package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;

public record UpcomingMeetingCard(
    String meetingId,
    String title,
    Instant startsAt,
    String roomName,
    JoinAvailability joinAvailability) {
}
