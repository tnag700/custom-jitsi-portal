package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingRole;

public record BulkInviteRecipientDto(
    String email,
    String userId,
    MeetingRole role,
    Integer rowIndex
) {}
