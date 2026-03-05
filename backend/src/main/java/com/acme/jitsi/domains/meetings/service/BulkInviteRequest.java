package com.acme.jitsi.domains.meetings.service;

import java.util.List;

public record BulkInviteRequest(
    List<BulkInviteRecipient> recipients,
    MeetingRole defaultRole,
    Integer defaultTtlMinutes,
    Integer defaultMaxUses,
    DuplicateHandlingPolicy duplicatePolicy
) {}
