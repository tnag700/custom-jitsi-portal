package com.acme.jitsi.domains.meetings.service;

public record BulkInviteError(
    int rowIndex,
    String recipient,
    String errorCode,
    String message
) {}
