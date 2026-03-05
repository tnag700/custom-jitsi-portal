package com.acme.jitsi.domains.meetings.service;

public record BulkInviteCreatedItem(
    String inviteId,
    String token,
    String recipient,
    String role
) {}
