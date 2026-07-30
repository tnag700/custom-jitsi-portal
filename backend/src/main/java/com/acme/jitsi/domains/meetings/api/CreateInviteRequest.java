package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateInviteRequest(
    @NotNull MeetingRole role,
    Integer maxUses,
    @NotNull @Min(1) @Max(168) Integer expiresInHours
) {}
