package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;

public record CreateBulkInvitesCommand(
    String meetingId,
    BulkInviteRequest request,
    String actorId,
    String traceId) {
}
