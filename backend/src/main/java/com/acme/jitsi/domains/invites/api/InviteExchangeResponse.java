package com.acme.jitsi.domains.invites.api;

import java.time.Instant;

record InviteExchangeResponse(
    String joinUrl,
    Instant expiresAt,
    String role,
    String meetingId) {
}