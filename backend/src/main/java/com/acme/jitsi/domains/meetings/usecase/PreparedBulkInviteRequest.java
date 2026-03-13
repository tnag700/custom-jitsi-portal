package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteHandlingStrategy;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import java.time.Instant;
import java.util.List;

record PreparedBulkInviteRequest(
    List<BulkInviteRecipient> recipients,
    MeetingRole defaultRole,
    int defaultMaxUses,
    Instant defaultExpiresAt,
    DuplicateInviteHandlingStrategy strategy) {}