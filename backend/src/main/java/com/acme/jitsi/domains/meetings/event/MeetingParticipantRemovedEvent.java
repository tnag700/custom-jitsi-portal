package com.acme.jitsi.domains.meetings.event;

public record MeetingParticipantRemovedEvent(
    String meetingId,
    String roomId,
    String actorId,
    String traceId,
    String changedFields,
    String subjectId
) {}
