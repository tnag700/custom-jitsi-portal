package com.acme.jitsi.domains.auth.service;

import java.time.Instant;

public record AuthRefreshSecurityEvent(
    String eventType,
    String errorCode,
    String tokenId,
    String subject,
    String meetingId,
    String traceId,
    Instant occurredAt) {
}
