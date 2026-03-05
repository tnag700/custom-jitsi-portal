package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.MeetingRole;
import java.time.Instant;

public record CreateInviteCommand(
    String meetingId,
    MeetingRole role,
    Integer maxUses,
    Instant expiresAt,
    String actorId,
    String traceId) {
}
