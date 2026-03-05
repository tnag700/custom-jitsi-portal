package com.acme.jitsi.domains.meetings.event;

public record MeetingInviteCreatedEvent(
    String meetingId,
    String roomId,
    String actorId,
    String traceId,
    String changedFields
) {}
