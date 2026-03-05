package com.acme.jitsi.domains.meetings.event;

public record MeetingParticipantRoleChangedEvent(
    String meetingId,
    String roomId,
    String actorId,
    String traceId,
    String changedFields,
    String subjectId
) {}
