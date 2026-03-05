package com.acme.jitsi.domains.meetings.usecase;

public record RevokeInviteCommand(
    String meetingId,
    String inviteId,
    String actorId,
    String traceId) {
}
