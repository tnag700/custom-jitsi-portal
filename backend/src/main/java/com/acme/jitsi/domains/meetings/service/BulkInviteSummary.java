package com.acme.jitsi.domains.meetings.service;

public record BulkInviteSummary(
    int total,
    int created,
    int skipped,
    int failed
) {}
