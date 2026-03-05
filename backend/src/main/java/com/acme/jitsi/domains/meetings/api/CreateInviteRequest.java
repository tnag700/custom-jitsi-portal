package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.validation.constraints.NotNull;

public record CreateInviteRequest(
    @NotNull MeetingRole role,
    Integer maxUses,
    Integer expiresInHours
) {}
