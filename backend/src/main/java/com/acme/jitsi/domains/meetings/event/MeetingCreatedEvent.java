package com.acme.jitsi.domains.meetings.event;

public record MeetingCreatedEvent(
    String roomId,
    String meetingId,
    String actorId,
    String traceId,
    String changedFields
) {}
