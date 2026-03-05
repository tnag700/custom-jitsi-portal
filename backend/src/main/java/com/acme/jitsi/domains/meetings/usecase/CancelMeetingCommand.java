package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.Meeting;

public record CancelMeetingCommand(
    Meeting existing,
    String actorId,
    String traceId) {
}
