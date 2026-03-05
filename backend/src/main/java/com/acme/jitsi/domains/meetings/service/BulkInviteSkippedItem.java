package com.acme.jitsi.domains.meetings.service;

public record BulkInviteSkippedItem(
    String recipient,
    String reason
) {}
