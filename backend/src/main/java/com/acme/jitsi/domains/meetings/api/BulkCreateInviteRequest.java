package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkCreateInviteRequest(
    @NotEmpty List<@Valid BulkInviteRecipientDto> recipients,
    Integer defaultTtlMinutes,
    Integer defaultMaxUses,
    MeetingRole defaultRole,
    DuplicateHandlingPolicy duplicatePolicy
) {}
