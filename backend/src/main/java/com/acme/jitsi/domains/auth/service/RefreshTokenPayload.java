package com.acme.jitsi.domains.auth.service;

import java.time.Instant;

record RefreshTokenPayload(
    String tokenId,
    String subject,
    String meetingId,
    Instant issuedAt,
    Instant expiresAt) {
}