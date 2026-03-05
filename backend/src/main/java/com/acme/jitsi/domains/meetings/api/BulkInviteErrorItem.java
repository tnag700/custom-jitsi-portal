package com.acme.jitsi.domains.meetings.api;

public record BulkInviteErrorItem(
    int rowIndex,
    String recipient,
    String errorCode,
    String message
) {}
