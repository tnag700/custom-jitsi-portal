package com.acme.jitsi.domains.meetings.event;

public record MeetingBulkInviteCreatedEvent(
    String meetingId,
    String roomId,
    String actorId,
    String traceId,
    String changedFields
) {}
