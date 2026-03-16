package com.acme.jitsi.domains.meetings.event;

import java.time.Instant;

public record MeetingJoinObservedEvent(
    String eventType,
    String result,
    String meetingId,
    String roomId,
    String subjectId,
    String role,
    String errorCode,
    String reasonCategory,
    String traceId,
    long durationMs,
    Instant occurredAt) {
}