package com.acme.jitsi.domains.auth.api;

import java.time.Instant;

record AuthRefreshResponse(String accessToken, String refreshToken, Instant expiresAt, String role, String tokenType) {
}
