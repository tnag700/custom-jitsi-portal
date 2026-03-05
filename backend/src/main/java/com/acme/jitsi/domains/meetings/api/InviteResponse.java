package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import java.time.Instant;

public record InviteResponse(
    String id,
    String meetingId,
    String token,
    String role,
    int maxUses,
    int usedCount,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt,
    String createdBy,
    boolean valid
) {
  public static InviteResponse fromDomain(MeetingInvite invite) {
    return new InviteResponse(
        invite.id(),
        invite.meetingId(),
        invite.token(),
        invite.role().value,
        invite.maxUses(),
        invite.usedCount(),
        invite.expiresAt(),
        invite.revokedAt(),
        invite.createdAt(),
        invite.createdBy(),
        invite.isValid()
    );
  }
}
